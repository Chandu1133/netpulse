package com.netpulse.anomaly;

import com.netpulse.config.NetPulseProperties;
import com.netpulse.domain.ChaosRule;
import com.netpulse.domain.Incident;
import com.netpulse.domain.IncidentSeverity;
import com.netpulse.domain.IncidentStatus;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProbeResult;
import com.netpulse.domain.TargetStatus;
import com.netpulse.dto.IncidentResponse;
import com.netpulse.dto.ProbeResultResponse;
import com.netpulse.dto.TargetResponse;
import com.netpulse.prober.ProbeResultListener;
import com.netpulse.repository.ChaosRuleRepository;
import com.netpulse.repository.IncidentRepository;
import com.netpulse.repository.MonitorTargetRepository;
import com.netpulse.repository.ProbeResultRepository;
import com.netpulse.service.OverviewService;
import com.netpulse.websocket.TelemetryBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class AnomalyDetectionService implements ProbeResultListener {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);
    private static final int MIN_BASELINE_SAMPLES = 3;

    private final SlidingWindowCalculator windowCalculator;
    private final ProbeResultRepository probeResultRepository;
    private final IncidentRepository incidentRepository;
    private final MonitorTargetRepository targetRepository;
    private final ChaosRuleRepository chaosRuleRepository;
    private final OverviewService overviewService;
    private final TelemetryBroadcaster broadcaster;
    private final NetPulseProperties properties;

    public AnomalyDetectionService(SlidingWindowCalculator windowCalculator,
                                   ProbeResultRepository probeResultRepository,
                                   IncidentRepository incidentRepository,
                                   MonitorTargetRepository targetRepository,
                                   ChaosRuleRepository chaosRuleRepository,
                                   OverviewService overviewService,
                                   TelemetryBroadcaster broadcaster,
                                   NetPulseProperties properties) {
        this.windowCalculator = windowCalculator;
        this.probeResultRepository = probeResultRepository;
        this.incidentRepository = incidentRepository;
        this.targetRepository = targetRepository;
        this.chaosRuleRepository = chaosRuleRepository;
        this.overviewService = overviewService;
        this.broadcaster = broadcaster;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void onProbeCompleted(MonitorTarget target, ProbeResult probe) {
        ensureWindowSeeded(target.getId(), probe.getId());

        RollingWindowStats stats = windowCalculator.evaluate(target.getId(), probe.getTotalLatencyMs());
        probe.setRollingMeanMs(stats.meanMs());
        probe.setRollingStdDevMs(stats.stdDevMs());
        probe.setZScore(stats.zScore());
        ProbeResult enrichedProbe = probeResultRepository.save(probe);

        Optional<DetectedAnomaly> detected = detectAnomaly(target, enrichedProbe, stats);
        Optional<Incident> openIncidentOpt = incidentRepository
                .findFirstByTargetIdAndStatusOrderByOpenedAtDesc(target.getId(), IncidentStatus.OPEN);

        IncidentEvent incidentEvent = null;

        if (detected.isPresent()) {
            DetectedAnomaly anomaly = detected.get();
            target.setConsecutiveHealthy(0);
            if (target.isEnabled()) {
                target.setStatus(anomaly.severity() == IncidentSeverity.CRITICAL
                        ? TargetStatus.DOWN
                        : TargetStatus.DEGRADED);
            }
            incidentEvent = openOrUpdateIncident(target, enrichedProbe, anomaly, openIncidentOpt.orElse(null));
        } else if (!enrichedProbe.isSuccess()) {
            // Single failure (below critical consecutive failure threshold)
            target.setConsecutiveHealthy(0);
            if (target.isEnabled()) {
                target.setStatus(TargetStatus.DEGRADED);
            }
            if (openIncidentOpt.isPresent()) {
                Incident open = openIncidentOpt.get();
                updatePeakMetrics(open, enrichedProbe);
                Incident saved = incidentRepository.save(open);
                incidentEvent = new IncidentEvent("UPDATED", saved);
            }
        } else {
            // Healthy probe cycle: add to rolling baseline window
            windowCalculator.recordHealthySample(target.getId(), enrichedProbe.getTotalLatencyMs());

            if (openIncidentOpt.isPresent()) {
                int autoResolveCycles = properties.getAnomaly().getAutoResolveHealthyCycles();
                if (target.getConsecutiveHealthy() >= autoResolveCycles) {
                    Incident open = openIncidentOpt.get();
                    open.setStatus(IncidentStatus.RESOLVED);
                    open.setResolvedAt(enrichedProbe.getCheckedAt() != null ? enrichedProbe.getCheckedAt() : Instant.now());
                    String existingCause = open.getRootCause() != null ? open.getRootCause() : "";
                    open.setRootCause(truncate(existingCause + " | Auto-resolved after "
                            + target.getConsecutiveHealthy() + " consecutive healthy probe cycles.", 1000));
                    Incident resolved = incidentRepository.save(open);
                    if (target.isEnabled()) {
                        target.setStatus(TargetStatus.HEALTHY);
                    }
                    incidentEvent = new IncidentEvent("RESOLVED", resolved);
                    log.info("Auto-resolved incident #{} for target '{}'", resolved.getId(), target.getName());
                } else if (target.isEnabled()) {
                    // Still recovering until consecutive healthy threshold is reached
                    target.setStatus(TargetStatus.DEGRADED);
                }
            } else if (target.isEnabled()) {
                target.setStatus(TargetStatus.HEALTHY);
            }
        }

        MonitorTarget savedTarget = targetRepository.save(target);

        boolean chaosActive = chaosRuleRepository.findByTargetId(savedTarget.getId())
                .map(ChaosRule::hasAnyFault)
                .orElse(false);
        long openIncidentCount = incidentRepository.countByTargetIdAndStatus(savedTarget.getId(), IncidentStatus.OPEN);

        broadcaster.broadcastProbeResult(ProbeResultResponse.from(enrichedProbe));
        broadcaster.broadcastTargetUpdate(TargetResponse.from(savedTarget, chaosActive, openIncidentCount));

        if (incidentEvent != null) {
            IncidentResponse incDto = IncidentResponse.from(incidentEvent.incident());
            switch (incidentEvent.action()) {
                case "OPENED" -> broadcaster.broadcastIncidentOpened(incDto);
                case "UPDATED" -> broadcaster.broadcastIncidentUpdated(incDto);
                case "RESOLVED" -> broadcaster.broadcastIncidentResolved(incDto);
                default -> {
                }
            }
        }

        broadcaster.broadcastOverview(overviewService.getOverview());
    }

    Optional<DetectedAnomaly> detectAnomaly(MonitorTarget target, ProbeResult probe, RollingWindowStats stats) {
        NetPulseProperties.Anomaly cfg = properties.getAnomaly();

        double packetLoss = probe.getPacketLossPct() != null ? probe.getPacketLossPct() : 0.0;
        Double zScore = stats.zScore();
        boolean hasBaseline = stats.sampleCount() >= MIN_BASELINE_SAMPLES && zScore != null;

        // 1. CRITICAL checks
        if (!probe.isSuccess() && target.getConsecutiveFailures() >= cfg.getCriticalConsecutiveFailures()) {
            String reason = probe.getErrorMessage() != null ? probe.getErrorMessage() : "Probe check failed";
            return Optional.of(new DetectedAnomaly(
                    IncidentSeverity.CRITICAL,
                    "CONSECUTIVE_FAILURE",
                    target.getName() + " failed " + target.getConsecutiveFailures() + " consecutive probes",
                    "Consecutive probe failures (" + target.getConsecutiveFailures() + "): " + reason
            ));
        }

        if (probe.isSuccess() && packetLoss >= cfg.getCriticalPacketLossPct()) {
            return Optional.of(new DetectedAnomaly(
                    IncidentSeverity.CRITICAL,
                    "PACKET_LOSS",
                    String.format("%s experiencing critical packet loss (%.1f%%)", target.getName(), packetLoss),
                    String.format("Packet loss %.1f%% exceeded critical threshold %.1f%%",
                            packetLoss, cfg.getCriticalPacketLossPct())
            ));
        }

        if (probe.isSuccess() && hasBaseline && zScore > cfg.getCriticalZThreshold()) {
            return Optional.of(new DetectedAnomaly(
                    IncidentSeverity.CRITICAL,
                    "LATENCY_SPIKE",
                    String.format("%s critical latency spike (%.1fms, z=%.2f)",
                            target.getName(), probe.getTotalLatencyMs(), zScore),
                    String.format("Latency %.1fms exceeded mu + 3*sigma (%.1fms; mean=%.1fms, std=%.1fms, z=%.2f)",
                            probe.getTotalLatencyMs(), stats.criticalThresholdMs(), stats.meanMs(), stats.stdDevMs(), zScore)
            ));
        }

        // 2. WARNING checks
        if (probe.isSuccess() && packetLoss >= cfg.getWarningPacketLossPct()) {
            return Optional.of(new DetectedAnomaly(
                    IncidentSeverity.WARNING,
                    "PACKET_LOSS",
                    String.format("%s experiencing elevated packet loss (%.1f%%)", target.getName(), packetLoss),
                    String.format("Packet loss %.1f%% exceeded warning threshold %.1f%%",
                            packetLoss, cfg.getWarningPacketLossPct())
            ));
        }

        if (probe.isSuccess() && hasBaseline && zScore > cfg.getWarningZThreshold()) {
            return Optional.of(new DetectedAnomaly(
                    IncidentSeverity.WARNING,
                    "LATENCY_SPIKE",
                    String.format("%s latency spike detected (%.1fms, z=%.2f)",
                            target.getName(), probe.getTotalLatencyMs(), zScore),
                    String.format("Latency %.1fms exceeded mu + 2*sigma (%.1fms; mean=%.1fms, std=%.1fms, z=%.2f)",
                            probe.getTotalLatencyMs(), stats.warningThresholdMs(), stats.meanMs(), stats.stdDevMs(), zScore)
            ));
        }

        if (probe.isSuccess() && probe.getTlsCertDaysRemaining() != null
                && probe.getTlsCertDaysRemaining() < cfg.getTlsExpiryWarningDays()) {
            return Optional.of(new DetectedAnomaly(
                    IncidentSeverity.WARNING,
                    "TLS_EXPIRY",
                    String.format("%s TLS certificate expires in %d days",
                            target.getName(), probe.getTlsCertDaysRemaining()),
                    String.format("X.509 certificate expires in %d days (threshold < %d days)",
                            probe.getTlsCertDaysRemaining(), cfg.getTlsExpiryWarningDays())
            ));
        }

        return Optional.empty();
    }

    private IncidentEvent openOrUpdateIncident(MonitorTarget target,
                                               ProbeResult probe,
                                               DetectedAnomaly anomaly,
                                               Incident existingOpen) {
        if (existingOpen == null) {
            Incident incident = new Incident();
            incident.setTarget(target);
            incident.setSeverity(anomaly.severity());
            incident.setStatus(IncidentStatus.OPEN);
            incident.setTriggerType(anomaly.triggerType());
            incident.setSummary(truncate(anomaly.summary(), 500));
            incident.setRootCause(truncate(anomaly.rootCause(), 1000));
            incident.setPeakLatencyMs(probe.getTotalLatencyMs());
            incident.setPeakPacketLossPct(probe.getPacketLossPct());
            incident.setPeakZScore(probe.getZScore());
            incident.setOpenedAt(probe.getCheckedAt() != null ? probe.getCheckedAt() : Instant.now());

            Incident saved = incidentRepository.save(incident);
            log.warn("Opened {} incident #{} for target '{}': {}",
                    saved.getSeverity(), saved.getId(), target.getName(), saved.getSummary());
            return new IncidentEvent("OPENED", saved);
        }

        // Deduplicate: update existing open incident and escalate severity if needed
        boolean escalated = existingOpen.getSeverity() == IncidentSeverity.WARNING
                && anomaly.severity() == IncidentSeverity.CRITICAL;
        if (escalated) {
            existingOpen.setSeverity(IncidentSeverity.CRITICAL);
            existingOpen.setTriggerType(anomaly.triggerType());
            existingOpen.setSummary(truncate(anomaly.summary(), 500));
            existingOpen.setRootCause(truncate(anomaly.rootCause(), 1000));
            log.warn("Escalated incident #{} for target '{}' to CRITICAL: {}",
                    existingOpen.getId(), target.getName(), anomaly.summary());
        }

        updatePeakMetrics(existingOpen, probe);
        Incident saved = incidentRepository.save(existingOpen);
        return new IncidentEvent("UPDATED", saved);
    }

    private void updatePeakMetrics(Incident incident, ProbeResult probe) {
        if (probe.getTotalLatencyMs() != null) {
            if (incident.getPeakLatencyMs() == null || probe.getTotalLatencyMs() > incident.getPeakLatencyMs()) {
                incident.setPeakLatencyMs(probe.getTotalLatencyMs());
            }
        }
        if (probe.getPacketLossPct() != null) {
            if (incident.getPeakPacketLossPct() == null || probe.getPacketLossPct() > incident.getPeakPacketLossPct()) {
                incident.setPeakPacketLossPct(probe.getPacketLossPct());
            }
        }
        if (probe.getZScore() != null) {
            if (incident.getPeakZScore() == null || probe.getZScore() > incident.getPeakZScore()) {
                incident.setPeakZScore(probe.getZScore());
            }
        }
    }

    private void ensureWindowSeeded(Long targetId, Long excludeProbeId) {
        if (windowCalculator.hasWindow(targetId)) {
            return;
        }
        List<ProbeResult> recentHealthy = probeResultRepository
                .findTop20ByTargetIdAndSuccessTrueOrderByCheckedAtDesc(targetId);

        List<Double> samples = new ArrayList<>();
        for (ProbeResult p : recentHealthy) {
            if (excludeProbeId != null && excludeProbeId.equals(p.getId())) {
                continue;
            }
            if (!p.isChaosInjected() && p.getTotalLatencyMs() != null) {
                samples.add(p.getTotalLatencyMs());
            }
        }
        Collections.reverse(samples);
        windowCalculator.seedWindow(targetId, samples);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    record DetectedAnomaly(
            IncidentSeverity severity,
            String triggerType,
            String summary,
            String rootCause
    ) {
    }

    private record IncidentEvent(String action, Incident incident) {
    }
}

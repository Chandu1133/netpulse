package com.netpulse.prober;

import com.netpulse.config.NetPulseProperties;
import com.netpulse.domain.ChaosRule;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProbeResult;
import com.netpulse.domain.TargetStatus;
import com.netpulse.dto.ProbeResultResponse;
import com.netpulse.exception.ResourceNotFoundException;
import com.netpulse.repository.ChaosRuleRepository;
import com.netpulse.repository.MonitorTargetRepository;
import com.netpulse.repository.ProbeResultRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ProbeOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProbeOrchestrator.class);

    private final MonitorTargetRepository targetRepository;
    private final ProbeResultRepository probeResultRepository;
    private final ChaosRuleRepository chaosRuleRepository;
    private final NetworkProbeClient probeClient;
    private final ChaosInterceptor chaosInterceptor;
    private final NetPulseProperties properties;
    private final List<ProbeResultListener> listeners;

    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<Long, Instant> lastDispatchedAt = new ConcurrentHashMap<>();
    private final Set<Long> inFlightTargets = ConcurrentHashMap.newKeySet();

    public ProbeOrchestrator(MonitorTargetRepository targetRepository,
                             ProbeResultRepository probeResultRepository,
                             ChaosRuleRepository chaosRuleRepository,
                             NetworkProbeClient probeClient,
                             ChaosInterceptor chaosInterceptor,
                             NetPulseProperties properties,
                             List<ProbeResultListener> listeners) {
        this.targetRepository = targetRepository;
        this.probeResultRepository = probeResultRepository;
        this.chaosRuleRepository = chaosRuleRepository;
        this.probeClient = probeClient;
        this.chaosInterceptor = chaosInterceptor;
        this.properties = properties;
        this.listeners = listeners != null ? listeners : List.of();
    }

    @Scheduled(fixedDelayString = "${netpulse.prober.scheduler-tick-ms:2000}", initialDelay = 3000)
    public void scheduledTick() {
        if (!properties.getProber().isEnabled()) {
            return;
        }
        runDueTargets();
    }

    public List<ProbeResult> runDueTargets() {
        Instant now = Instant.now();
        List<MonitorTarget> dueTargets = targetRepository.findByEnabledTrueOrderByIdAsc().stream()
                .filter(target -> isDue(target, now))
                .filter(target -> inFlightTargets.add(target.getId()))
                .toList();

        if (dueTargets.isEmpty()) {
            return List.of();
        }

        return executeBatchConcurrently(dueTargets, now);
    }

    public ProbeResultResponse triggerProbeNow(Long targetId) {
        MonitorTarget target = targetRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitor target not found: " + targetId));

        ProbeResult result = executeSingleTargetWithTimeout(target);
        lastDispatchedAt.put(target.getId(), Instant.now());
        return ProbeResultResponse.from(result);
    }

    List<ProbeResult> executeBatchConcurrently(List<MonitorTarget> targets, Instant dispatchTime) {
        List<Future<ProbeResult>> futures = new ArrayList<>(targets.size());
        for (MonitorTarget target : targets) {
            lastDispatchedAt.put(target.getId(), dispatchTime);
            futures.add(virtualExecutor.submit(() -> {
                try {
                    return executeSingleTargetWithTimeout(target);
                } finally {
                    inFlightTargets.remove(target.getId());
                }
            }));
        }

        List<ProbeResult> results = new ArrayList<>(targets.size());
        for (Future<ProbeResult> future : futures) {
            try {
                ProbeResult result = future.get();
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception ex) {
                log.warn("Probe batch task failed: {}", ex.getMessage());
            }
        }
        return results;
    }

    ProbeResult executeSingleTargetWithTimeout(MonitorTarget target) {
        int configuredTimeoutMs = target.getTimeoutMs() != null
                ? target.getTimeoutMs()
                : properties.getProber().getDefaultTimeoutMs();
        long hardTimeoutMs = configuredTimeoutMs + 2500L;

        ChaosRule activeRule = chaosRuleRepository.findByTargetId(target.getId())
                .filter(ChaosRule::hasAnyFault)
                .orElse(null);

        ProbeExecutionResult executionResult;
        Optional<ProbeExecutionResult> shortCircuit = chaosInterceptor.preProbeShortCircuit(target, activeRule);
        if (shortCircuit.isPresent()) {
            executionResult = shortCircuit.get();
        } else {
            Future<ProbeExecutionResult> probeFuture = virtualExecutor.submit(() -> probeClient.executeProbe(target));
            try {
                ProbeExecutionResult raw = probeFuture.get(hardTimeoutMs, TimeUnit.MILLISECONDS);
                executionResult = chaosInterceptor.applyPostProbe(raw, activeRule);
            } catch (TimeoutException tex) {
                probeFuture.cancel(true);
                executionResult = ProbeExecutionResult.failure(
                        Instant.now(),
                        null,
                        null,
                        null,
                        null,
                        (double) configuredTimeoutMs,
                        100.0,
                        0.0,
                        false,
                        "Probe exceeded hard timeout of " + hardTimeoutMs + "ms"
                );
            } catch (Exception ex) {
                executionResult = ProbeExecutionResult.failure(
                        Instant.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        100.0,
                        0.0,
                        false,
                        "Probe execution error: " + ex.getMessage()
                );
            }
        }

        return persistAndNotify(target.getId(), executionResult);
    }

    private ProbeResult persistAndNotify(Long targetId, ProbeExecutionResult exec) {
        MonitorTarget target = targetRepository.findById(targetId).orElse(null);
        if (target == null) {
            return null;
        }

        ProbeResult probe = new ProbeResult();
        probe.setTarget(target);
        probe.setCheckedAt(exec.checkedAt());
        probe.setSuccess(exec.success());
        probe.setResolvedIp(exec.resolvedIp());
        probe.setDnsTimeMs(exec.dnsTimeMs());
        probe.setTcpConnectMs(exec.tcpConnectMs());
        probe.setTlsHandshakeMs(exec.tlsHandshakeMs());
        probe.setTtfbMs(exec.ttfbMs());
        probe.setTotalLatencyMs(exec.totalLatencyMs());
        probe.setHttpStatusCode(exec.httpStatusCode());
        probe.setTlsCertDaysRemaining(exec.tlsCertDaysRemaining());
        probe.setPacketLossPct(exec.packetLossPct());
        probe.setJitterMs(exec.jitterMs());
        probe.setChaosInjected(exec.chaosInjected());
        probe.setErrorMessage(exec.errorMessage());

        ProbeResult saved = probeResultRepository.save(probe);

        target.setLastCheckedAt(saved.getCheckedAt());
        target.setLastTotalLatencyMs(saved.getTotalLatencyMs());
        target.setLastPacketLossPct(saved.getPacketLossPct());

        if (saved.isSuccess()) {
            target.setConsecutiveFailures(0);
            target.setConsecutiveHealthy(target.getConsecutiveHealthy() + 1);
            if (target.isEnabled()) {
                double loss = saved.getPacketLossPct() != null ? saved.getPacketLossPct() : 0.0;
                if (loss >= properties.getAnomaly().getCriticalPacketLossPct()) {
                    target.setStatus(TargetStatus.DOWN);
                } else if (loss >= properties.getAnomaly().getWarningPacketLossPct()) {
                    target.setStatus(TargetStatus.DEGRADED);
                } else {
                    target.setStatus(TargetStatus.HEALTHY);
                }
            }
        } else {
            target.setConsecutiveHealthy(0);
            target.setConsecutiveFailures(target.getConsecutiveFailures() + 1);
            if (target.isEnabled()) {
                target.setStatus(target.getConsecutiveFailures() >= properties.getAnomaly().getCriticalConsecutiveFailures()
                        ? TargetStatus.DOWN
                        : TargetStatus.DEGRADED);
            }
        }

        MonitorTarget updatedTarget = targetRepository.save(target);

        for (ProbeResultListener listener : listeners) {
            try {
                listener.onProbeCompleted(updatedTarget, saved);
            } catch (Exception ex) {
                log.error("ProbeResultListener {} failed for target {}: {}",
                        listener.getClass().getSimpleName(), targetId, ex.getMessage(), ex);
            }
        }

        return saved;
    }

    private boolean isDue(MonitorTarget target, Instant now) {
        Instant last = lastDispatchedAt.get(target.getId());
        if (last == null) {
            return true;
        }
        int intervalSec = target.getIntervalSeconds() != null ? target.getIntervalSeconds() : 10;
        return Duration.between(last, now).toMillis() >= (intervalSec * 1000L);
    }

    @PreDestroy
    void shutdown() {
        virtualExecutor.shutdownNow();
    }
}

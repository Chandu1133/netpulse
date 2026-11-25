package com.netpulse.anomaly;

import com.netpulse.config.NetPulseProperties;
import com.netpulse.domain.Incident;
import com.netpulse.domain.IncidentSeverity;
import com.netpulse.domain.IncidentStatus;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProbeResult;
import com.netpulse.domain.ProtocolType;
import com.netpulse.domain.TargetStatus;
import com.netpulse.dto.OverviewResponse;
import com.netpulse.repository.ChaosRuleRepository;
import com.netpulse.repository.IncidentRepository;
import com.netpulse.repository.MonitorTargetRepository;
import com.netpulse.repository.ProbeResultRepository;
import com.netpulse.service.OverviewService;
import com.netpulse.websocket.TelemetryBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private ProbeResultRepository probeResultRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private MonitorTargetRepository targetRepository;

    @Mock
    private ChaosRuleRepository chaosRuleRepository;

    @Mock
    private OverviewService overviewService;

    @Mock
    private TelemetryBroadcaster broadcaster;

    private SlidingWindowCalculator calculator;
    private AnomalyDetectionService service;
    private MonitorTarget target;
    private AtomicReference<Incident> storedOpenIncident;

    @BeforeEach
    void setUp() {
        NetPulseProperties props = new NetPulseProperties();
        calculator = new SlidingWindowCalculator(props);
        service = new AnomalyDetectionService(
                calculator,
                probeResultRepository,
                incidentRepository,
                targetRepository,
                chaosRuleRepository,
                overviewService,
                broadcaster,
                props
        );

        target = new MonitorTarget("Cloudflare", "cloudflare.com", 443, "/", ProtocolType.HTTPS, 10, 3000);
        target.setId(1L);

        storedOpenIncident = new AtomicReference<>(null);

        lenient().when(probeResultRepository.save(any(ProbeResult.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(targetRepository.save(any(MonitorTarget.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(chaosRuleRepository.findByTargetId(1L)).thenReturn(Optional.empty());
        lenient().when(overviewService.getOverview()).thenReturn(
                new OverviewResponse(1, 1, 1, 0, 0, 0, 0, 100.0, 0, 0));
        lenient().when(incidentRepository.findFirstByTargetIdAndStatusOrderByOpenedAtDesc(1L, IncidentStatus.OPEN))
                .thenAnswer(inv -> Optional.ofNullable(storedOpenIncident.get()));
        lenient().when(incidentRepository.countByTargetIdAndStatus(1L, IncidentStatus.OPEN))
                .thenAnswer(inv -> storedOpenIncident.get() != null ? 1L : 0L);
        lenient().when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident inc = inv.getArgument(0);
            if (inc.getId() == null) {
                inc.setId(10L);
            }
            if (inc.getStatus() == IncidentStatus.OPEN) {
                storedOpenIncident.set(inc);
            } else {
                storedOpenIncident.set(null);
            }
            return inc;
        });

        // Seed stable baseline: mean = 100ms, std = 10ms -> warning > 120ms, critical > 130ms
        calculator.seedWindow(1L, List.of(90.0, 110.0, 90.0, 110.0, 90.0, 110.0));
    }

    @Test
    void latencySpikeOpensWarningEscalatesToCriticalAndAutoResolvesAfterThreeHealthyCycles() {
        // 1. Spike at 125ms (z = 2.5) -> opens WARNING
        ProbeResult warnProbe = healthyProbe(125.0, 0.0, 60);
        service.onProbeCompleted(target, warnProbe);

        assertThat(storedOpenIncident.get()).isNotNull();
        assertThat(storedOpenIncident.get().getSeverity()).isEqualTo(IncidentSeverity.WARNING);
        assertThat(storedOpenIncident.get().getTriggerType()).isEqualTo("LATENCY_SPIKE");
        assertThat(storedOpenIncident.get().getPeakLatencyMs()).isEqualTo(125.0);
        assertThat(target.getStatus()).isEqualTo(TargetStatus.DEGRADED);
        verify(broadcaster).broadcastIncidentOpened(any());

        // 2. Bigger spike at 150ms (z = 5.0) -> deduplicates into SAME incident & escalates to CRITICAL
        ProbeResult critProbe = healthyProbe(150.0, 0.0, 60);
        service.onProbeCompleted(target, critProbe);

        assertThat(storedOpenIncident.get()).isNotNull();
        assertThat(storedOpenIncident.get().getId()).isEqualTo(10L);
        assertThat(storedOpenIncident.get().getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(storedOpenIncident.get().getPeakLatencyMs()).isEqualTo(150.0);
        assertThat(storedOpenIncident.get().getPeakZScore()).isEqualTo(5.0);
        assertThat(target.getStatus()).isEqualTo(TargetStatus.DOWN);
        verify(broadcaster).broadcastIncidentUpdated(any());

        // 3. Two healthy cycles -> incident MUST remain OPEN
        target.setConsecutiveHealthy(1);
        service.onProbeCompleted(target, healthyProbe(100.0, 0.0, 60));
        assertThat(storedOpenIncident.get()).isNotNull();

        target.setConsecutiveHealthy(2);
        service.onProbeCompleted(target, healthyProbe(101.0, 0.0, 60));
        assertThat(storedOpenIncident.get()).isNotNull();
        verify(broadcaster, never()).broadcastIncidentResolved(any());

        // 4. Third consecutive healthy cycle -> AUTO-RESOLVES
        Incident incidentRef = storedOpenIncident.get();
        target.setConsecutiveHealthy(3);
        service.onProbeCompleted(target, healthyProbe(99.0, 0.0, 60));

        assertThat(storedOpenIncident.get()).isNull();
        assertThat(incidentRef.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incidentRef.getResolvedAt()).isNotNull();
        assertThat(incidentRef.getPeakLatencyMs()).isEqualTo(150.0);
        assertThat(target.getStatus()).isEqualTo(TargetStatus.HEALTHY);
        verify(broadcaster).broadcastIncidentResolved(any());
    }

    @Test
    void packetLossTriggersWarningAndEscalatesToCritical() {
        // 33.3% loss -> WARNING
        service.onProbeCompleted(target, healthyProbe(100.0, 33.3, 60));
        assertThat(storedOpenIncident.get()).isNotNull();
        assertThat(storedOpenIncident.get().getSeverity()).isEqualTo(IncidentSeverity.WARNING);
        assertThat(storedOpenIncident.get().getTriggerType()).isEqualTo("PACKET_LOSS");

        // 66.7% loss -> escalates to CRITICAL
        service.onProbeCompleted(target, healthyProbe(100.0, 66.7, 60));
        assertThat(storedOpenIncident.get().getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(storedOpenIncident.get().getPeakPacketLossPct()).isEqualTo(66.7);
        assertThat(target.getStatus()).isEqualTo(TargetStatus.DOWN);
    }

    @Test
    void twoConsecutiveProbeFailuresTriggerCriticalIncident() {
        // 1st failure -> DEGRADED, no incident yet
        target.setConsecutiveFailures(1);
        target.setConsecutiveHealthy(0);
        service.onProbeCompleted(target, failedProbe("Connection timed out"));
        assertThat(storedOpenIncident.get()).isNull();
        assertThat(target.getStatus()).isEqualTo(TargetStatus.DEGRADED);

        // 2nd consecutive failure -> CRITICAL incident
        target.setConsecutiveFailures(2);
        service.onProbeCompleted(target, failedProbe("Connection timed out"));
        assertThat(storedOpenIncident.get()).isNotNull();
        assertThat(storedOpenIncident.get().getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(storedOpenIncident.get().getTriggerType()).isEqualTo("CONSECUTIVE_FAILURE");
        assertThat(target.getStatus()).isEqualTo(TargetStatus.DOWN);
        verify(broadcaster, atLeastOnce()).broadcastProbeResult(any());
    }

    @Test
    void expiringTlsCertificateTriggersWarningIncident() {
        service.onProbeCompleted(target, healthyProbe(100.0, 0.0, 10));

        assertThat(storedOpenIncident.get()).isNotNull();
        assertThat(storedOpenIncident.get().getSeverity()).isEqualTo(IncidentSeverity.WARNING);
        assertThat(storedOpenIncident.get().getTriggerType()).isEqualTo("TLS_EXPIRY");
        assertThat(storedOpenIncident.get().getSummary()).contains("10 days");
    }

    private ProbeResult healthyProbe(double latencyMs, double packetLossPct, Integer certDays) {
        ProbeResult p = new ProbeResult();
        p.setId(System.nanoTime());
        p.setTarget(target);
        p.setCheckedAt(Instant.now());
        p.setSuccess(true);
        p.setTotalLatencyMs(latencyMs);
        p.setPacketLossPct(packetLossPct);
        p.setTlsCertDaysRemaining(certDays);
        return p;
    }

    private ProbeResult failedProbe(String error) {
        ProbeResult p = new ProbeResult();
        p.setId(System.nanoTime());
        p.setTarget(target);
        p.setCheckedAt(Instant.now());
        p.setSuccess(false);
        p.setPacketLossPct(100.0);
        p.setErrorMessage(error);
        return p;
    }
}

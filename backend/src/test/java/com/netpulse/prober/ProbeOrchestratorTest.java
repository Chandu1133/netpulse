package com.netpulse.prober;

import com.netpulse.config.NetPulseProperties;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProbeResult;
import com.netpulse.domain.ProtocolType;
import com.netpulse.domain.TargetStatus;
import com.netpulse.repository.ChaosRuleRepository;
import com.netpulse.repository.MonitorTargetRepository;
import com.netpulse.repository.ProbeResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProbeOrchestratorTest {

    @Mock
    private MonitorTargetRepository targetRepository;

    @Mock
    private ProbeResultRepository probeResultRepository;

    @Mock
    private ChaosRuleRepository chaosRuleRepository;

    @Mock
    private ProbeResultListener listener;

    @Test
    void executesTargetsConcurrentlyOnVirtualThreadsAndIsolatesSlowHostTimeouts() {
        MonitorTarget fastTarget = new MonitorTarget("Fast Host", "fast.local", 80, "/", ProtocolType.HTTP, 10, 500);
        fastTarget.setId(1L);

        MonitorTarget slowTarget = new MonitorTarget("Slow Host", "slow.local", 80, "/", ProtocolType.HTTP, 10, 200);
        slowTarget.setId(2L);

        NetworkProbeClient mockClient = target -> {
            if (target.getId().equals(2L)) {
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new ProbeExecutionResult(
                    Instant.now(), true, "127.0.0.1",
                    1.0, 2.0, null, 5.0, 8.0, 200, null, 0.0, 0.2, false, null
            );
        };

        when(targetRepository.findById(1L)).thenReturn(Optional.of(fastTarget));
        when(targetRepository.findById(2L)).thenReturn(Optional.of(slowTarget));
        when(chaosRuleRepository.findByTargetId(any())).thenReturn(Optional.empty());
        when(probeResultRepository.save(any(ProbeResult.class))).thenAnswer(inv -> {
            ProbeResult p = inv.getArgument(0);
            p.setId(p.getTarget().getId() * 100);
            return p;
        });
        when(targetRepository.save(any(MonitorTarget.class))).thenAnswer(inv -> inv.getArgument(0));

        NetPulseProperties props = new NetPulseProperties();
        ProbeOrchestrator orchestrator = new ProbeOrchestrator(
                targetRepository,
                probeResultRepository,
                chaosRuleRepository,
                mockClient,
                new ChaosInterceptor(new Random(1L)),
                props,
                List.of(listener)
        );

        long startMs = System.currentTimeMillis();
        List<ProbeResult> results = orchestrator.executeBatchConcurrently(List.of(fastTarget, slowTarget), Instant.now());
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertThat(results).hasSize(2);
        // Hard timeout is configuredTimeout (200ms) + 2500ms = 2700ms, well below the 6000ms sleep
        assertThat(elapsedMs).isLessThan(4500L);

        ProbeResult fastResult = results.stream().filter(r -> r.getTarget().getId().equals(1L)).findFirst().orElseThrow();
        ProbeResult slowResult = results.stream().filter(r -> r.getTarget().getId().equals(2L)).findFirst().orElseThrow();

        assertThat(fastResult.isSuccess()).isTrue();
        assertThat(fastTarget.getStatus()).isEqualTo(TargetStatus.HEALTHY);

        assertThat(slowResult.isSuccess()).isFalse();
        assertThat(slowResult.getErrorMessage()).contains("hard timeout");
        assertThat(slowTarget.getConsecutiveFailures()).isEqualTo(1);

        verify(listener).onProbeCompleted(fastTarget, fastResult);
        verify(listener).onProbeCompleted(slowTarget, slowResult);
    }
}

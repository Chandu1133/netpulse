package com.netpulse.prober;

import com.netpulse.config.NetPulseProperties;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProbeResult;
import com.netpulse.domain.ProtocolType;
import com.netpulse.repository.ChaosRuleRepository;
import com.netpulse.repository.MonitorTargetRepository;
import com.netpulse.repository.ProbeResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProbeConcurrencyBenchmarkTest {

    @Mock
    private MonitorTargetRepository targetRepository;

    @Mock
    private ProbeResultRepository probeResultRepository;

    @Mock
    private ChaosRuleRepository chaosRuleRepository;

    @Test
    void benchmarksVirtualThreadConcurrentProbingVsSequentialAcrossFiftyTargets() {
        final int targetCount = 50;
        final long simulatedIoLatencyMs = 100L;

        List<MonitorTarget> targets = new ArrayList<>(targetCount);
        Map<Long, MonitorTarget> byId = new HashMap<>();
        for (int i = 1; i <= targetCount; i++) {
            MonitorTarget t = new MonitorTarget(
                    "Target-" + i, "host-" + i + ".example.com", 443, "/",
                    ProtocolType.HTTPS, 10, 3000);
            t.setId((long) i);
            targets.add(t);
            byId.put((long) i, t);
        }

        AtomicInteger virtualThreadHits = new AtomicInteger(0);
        NetworkProbeClient simulatedIoClient = target -> {
            if (Thread.currentThread().isVirtual()) {
                virtualThreadHits.incrementAndGet();
            }
            try {
                Thread.sleep(simulatedIoLatencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ProbeExecutionResult(
                    Instant.now(), true, "104.16.132.229",
                    4.0, 12.0, 18.0, 66.0, 100.0, 200, 75, 0.0, 1.2, false, null
            );
        };

        when(targetRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(byId.get(inv.getArgument(0))));
        when(chaosRuleRepository.findByTargetId(any())).thenReturn(Optional.empty());
        when(probeResultRepository.save(any(ProbeResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(targetRepository.save(any(MonitorTarget.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Sequential baseline (probing 50 targets one-by-one)
        long seqStart = System.nanoTime();
        for (MonitorTarget target : targets) {
            simulatedIoClient.executeProbe(target);
        }
        double sequentialMs = (System.nanoTime() - seqStart) / 1_000_000.0;

        // 2. Concurrent execution via Java 21 Virtual Threads in ProbeOrchestrator
        virtualThreadHits.set(0);
        ProbeOrchestrator orchestrator = new ProbeOrchestrator(
                targetRepository,
                probeResultRepository,
                chaosRuleRepository,
                simulatedIoClient,
                new ChaosInterceptor(new Random(7L)),
                new NetPulseProperties(),
                List.of()
        );

        long vtStart = System.nanoTime();
        List<ProbeResult> results = orchestrator.executeBatchConcurrently(targets, Instant.now());
        double virtualThreadsMs = (System.nanoTime() - vtStart) / 1_000_000.0;

        double speedup = sequentialMs / Math.max(1.0, virtualThreadsMs);
        System.out.printf(
                "[NETPULSE BENCHMARK] targets=%d simulatedIo=%dms | sequential=%.1fms | virtualThreads=%.1fms | speedup=%.1fx | virtualThreadProbes=%d/%d%n",
                targetCount, simulatedIoLatencyMs, sequentialMs, virtualThreadsMs, speedup,
                virtualThreadHits.get(), targetCount
        );

        assertThat(results).hasSize(targetCount);
        assertThat(virtualThreadHits.get()).isEqualTo(targetCount);
        assertThat(virtualThreadsMs).isLessThan(sequentialMs / 5.0);
    }
}

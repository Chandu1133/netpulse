package com.netpulse.prober;

import com.netpulse.domain.ChaosRule;
import com.netpulse.domain.MonitorTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

@Component
public class ChaosInterceptor {

    private final Random random;

    @Autowired
    public ChaosInterceptor() {
        this(new Random());
    }

    public ChaosInterceptor(Random random) {
        this.random = random;
    }

    /**
     * Checks whether a chaos rule should short-circuit the probe before making network calls.
     */
    public Optional<ProbeExecutionResult> preProbeShortCircuit(MonitorTarget target, ChaosRule rule) {
        if (rule == null || !rule.hasAnyFault()) {
            return Optional.empty();
        }
        Instant now = Instant.now();

        if (rule.isForceDnsFailure()) {
            double simulatedDnsMs = round2(2.0 + random.nextDouble() * 6.0);
            return Optional.of(ProbeExecutionResult.failure(
                    now,
                    null,
                    simulatedDnsMs,
                    null,
                    null,
                    simulatedDnsMs,
                    100.0,
                    0.0,
                    true,
                    "Chaos injected: Simulated DNS resolution failure (NXDOMAIN)"
            ));
        }

        if (rule.isForceTimeout()) {
            double timeoutMs = target.getTimeoutMs() != null ? target.getTimeoutMs().doubleValue() : 3000.0;
            return Optional.of(ProbeExecutionResult.failure(
                    now,
                    null,
                    round2(1.0 + random.nextDouble() * 3.0),
                    null,
                    null,
                    timeoutMs,
                    100.0,
                    0.0,
                    true,
                    "Chaos injected: Simulated connection timeout (" + (int) timeoutMs + "ms)"
            ));
        }

        return Optional.empty();
    }

    /**
     * Applies latency, jitter, and packet drop faults to a completed probe result.
     */
    public ProbeExecutionResult applyPostProbe(ProbeExecutionResult original, ChaosRule rule) {
        if (rule == null || !rule.hasAnyFault()) {
            return original;
        }

        double extraLatency = rule.getAddedLatencyMs();
        double extraJitterSample = rule.getAddedJitterMs() > 0
                ? round2(random.nextDouble() * rule.getAddedJitterMs())
                : 0.0;
        double totalAddedDelay = round2(extraLatency + extraJitterSample);

        Double adjustedTtfb = original.ttfbMs();
        Double adjustedTcp = original.tcpConnectMs();
        Double adjustedDns = original.dnsTimeMs();

        if (totalAddedDelay > 0.0) {
            if (adjustedTtfb != null) {
                adjustedTtfb = round2(adjustedTtfb + totalAddedDelay);
            } else if (adjustedTcp != null) {
                adjustedTcp = round2(adjustedTcp + totalAddedDelay);
            } else if (adjustedDns != null) {
                adjustedDns = round2(adjustedDns + totalAddedDelay);
            }
        }

        double baseTotal = original.totalLatencyMs() != null ? original.totalLatencyMs() : 0.0;
        double adjustedTotal = round2(baseTotal + totalAddedDelay);

        double baseJitter = original.jitterMs() != null ? original.jitterMs() : 0.0;
        double adjustedJitter = rule.getAddedJitterMs() > 0
                ? round2(baseJitter + (rule.getAddedJitterMs() * (0.35 + random.nextDouble() * 0.45)))
                : baseJitter;

        double baseLoss = original.packetLossPct() != null ? original.packetLossPct() : 0.0;
        double adjustedLoss = baseLoss;
        boolean success = original.success();
        String errorMessage = original.errorMessage();

        if (rule.getPacketDropPct() > 0.0) {
            if (rule.getPacketDropPct() >= 100.0) {
                adjustedLoss = 100.0;
                success = false;
                errorMessage = "Chaos injected: 100% packet drop";
            } else {
                // Realistic packet drop centered tightly around configured percentage (+/- 4%)
                double variance = (random.nextDouble() * 8.0) - 4.0;
                double injectedLoss = clamp(rule.getPacketDropPct() + variance, 1.0, 99.0);
                adjustedLoss = round2(Math.min(100.0, baseLoss + (100.0 - baseLoss) * (injectedLoss / 100.0)));
            }
        }

        return new ProbeExecutionResult(
                original.checkedAt(),
                success,
                original.resolvedIp(),
                adjustedDns,
                adjustedTcp,
                original.tlsHandshakeMs(),
                adjustedTtfb,
                adjustedTotal,
                original.httpStatusCode(),
                original.tlsCertDaysRemaining(),
                adjustedLoss,
                adjustedJitter,
                true,
                errorMessage
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

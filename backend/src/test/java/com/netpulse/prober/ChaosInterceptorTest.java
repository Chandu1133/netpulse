package com.netpulse.prober;

import com.netpulse.domain.ChaosRule;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProtocolType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ChaosInterceptorTest {

    private final ChaosInterceptor interceptor = new ChaosInterceptor(new Random(42L));

    private MonitorTarget sampleTarget() {
        MonitorTarget target = new MonitorTarget("Cloudflare", "cloudflare.com", 443, "/",
                ProtocolType.HTTPS, 10, 3000);
        target.setId(1L);
        return target;
    }

    private ProbeExecutionResult healthyResult() {
        return new ProbeExecutionResult(
                Instant.now(),
                true,
                "104.16.132.229",
                5.0,
                12.0,
                18.0,
                25.0,
                60.0,
                200,
                65,
                0.0,
                1.5,
                false,
                null
        );
    }

    @Test
    void forceDnsFailureShortCircuitsBeforeNetworkProbe() {
        ChaosRule rule = new ChaosRule();
        rule.setForceDnsFailure(true);
        rule.setActive(true);

        Optional<ProbeExecutionResult> result = interceptor.preProbeShortCircuit(sampleTarget(), rule);

        assertThat(result).isPresent();
        assertThat(result.get().success()).isFalse();
        assertThat(result.get().chaosInjected()).isTrue();
        assertThat(result.get().packetLossPct()).isEqualTo(100.0);
        assertThat(result.get().errorMessage()).contains("DNS");
    }

    @Test
    void forceTimeoutShortCircuitsWithConfiguredTimeout() {
        ChaosRule rule = new ChaosRule();
        rule.setForceTimeout(true);
        rule.setActive(true);

        Optional<ProbeExecutionResult> result = interceptor.preProbeShortCircuit(sampleTarget(), rule);

        assertThat(result).isPresent();
        assertThat(result.get().success()).isFalse();
        assertThat(result.get().chaosInjected()).isTrue();
        assertThat(result.get().totalLatencyMs()).isEqualTo(3000.0);
        assertThat(result.get().errorMessage()).contains("timeout");
    }

    @Test
    void addedLatencyJitterAndPacketDropModifyResult() {
        ChaosRule rule = new ChaosRule();
        rule.setAddedLatencyMs(300);
        rule.setAddedJitterMs(40);
        rule.setPacketDropPct(40.0);
        rule.setActive(true);

        ProbeExecutionResult modified = interceptor.applyPostProbe(healthyResult(), rule);

        assertThat(modified.chaosInjected()).isTrue();
        assertThat(modified.success()).isTrue();
        assertThat(modified.totalLatencyMs()).isGreaterThanOrEqualTo(360.0);
        assertThat(modified.ttfbMs()).isGreaterThanOrEqualTo(325.0);
        assertThat(modified.jitterMs()).isGreaterThan(1.5);
        assertThat(modified.packetLossPct()).isBetween(35.0, 45.0);
    }

    @Test
    void fullPacketDropMarksProbeFailed() {
        ChaosRule rule = new ChaosRule();
        rule.setPacketDropPct(100.0);
        rule.setActive(true);

        ProbeExecutionResult modified = interceptor.applyPostProbe(healthyResult(), rule);

        assertThat(modified.success()).isFalse();
        assertThat(modified.packetLossPct()).isEqualTo(100.0);
        assertThat(modified.errorMessage()).contains("100% packet drop");
    }

    @Test
    void inactiveRuleLeavesResultUnchanged() {
        ChaosRule rule = new ChaosRule();
        rule.setAddedLatencyMs(500);
        rule.setActive(false);

        ProbeExecutionResult original = healthyResult();
        assertThat(interceptor.preProbeShortCircuit(sampleTarget(), rule)).isEmpty();
        assertThat(interceptor.applyPostProbe(original, rule)).isSameAs(original);
    }
}

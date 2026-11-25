package com.netpulse.anomaly;

import com.netpulse.config.NetPulseProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowCalculatorTest {

    @Test
    void computesMeanStdDevThresholdsAndZScoreAccurately() {
        SlidingWindowCalculator calculator = new SlidingWindowCalculator(new NetPulseProperties());
        // Mean = 100.0, Population StdDev = 10.0
        calculator.seedWindow(1L, List.of(90.0, 110.0, 90.0, 110.0, 90.0, 110.0));

        RollingWindowStats warningStats = calculator.evaluate(1L, 125.0);
        assertThat(warningStats.sampleCount()).isEqualTo(6);
        assertThat(warningStats.meanMs()).isEqualTo(100.0);
        assertThat(warningStats.stdDevMs()).isEqualTo(10.0);
        assertThat(warningStats.warningThresholdMs()).isEqualTo(120.0);
        assertThat(warningStats.criticalThresholdMs()).isEqualTo(130.0);
        assertThat(warningStats.zScore()).isEqualTo(2.5);

        RollingWindowStats criticalStats = calculator.evaluate(1L, 135.0);
        assertThat(criticalStats.zScore()).isEqualTo(3.5);
    }

    @Test
    void enforcesMaxWindowSizeOfTwentySamples() {
        SlidingWindowCalculator calculator = new SlidingWindowCalculator(new NetPulseProperties());

        // Push 20 samples of 50ms, then 20 samples of 100ms
        for (int i = 0; i < 20; i++) {
            calculator.recordHealthySample(2L, 50.0);
        }
        for (int i = 0; i < 20; i++) {
            calculator.recordHealthySample(2L, 100.0);
        }

        RollingWindowStats stats = calculator.evaluate(2L, 100.0);
        assertThat(stats.sampleCount()).isEqualTo(20);
        assertThat(stats.meanMs()).isEqualTo(100.0);
        assertThat(stats.zScore()).isEqualTo(0.0);
    }

    @Test
    void appliesMinimumStdDevFloorToPreventZeroVarianceFalsePositives() {
        SlidingWindowCalculator calculator = new SlidingWindowCalculator(new NetPulseProperties());
        calculator.seedWindow(3L, List.of(10.0, 10.0, 10.0, 10.0));

        RollingWindowStats stats = calculator.evaluate(3L, 12.0);
        // Floor is 5.0ms so 12ms vs 10ms gives z = 0.4, not Infinity
        assertThat(stats.stdDevMs()).isEqualTo(5.0);
        assertThat(stats.zScore()).isEqualTo(0.4);
    }
}

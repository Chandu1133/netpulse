package com.netpulse.anomaly;

public record RollingWindowStats(
        int sampleCount,
        Double meanMs,
        Double stdDevMs,
        Double warningThresholdMs,
        Double criticalThresholdMs,
        Double zScore
) {
    public static RollingWindowStats empty() {
        return new RollingWindowStats(0, null, null, null, null, null);
    }
}

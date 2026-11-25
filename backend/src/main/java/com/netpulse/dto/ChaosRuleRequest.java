package com.netpulse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChaosRuleRequest(
        @Min(value = 0, message = "addedLatencyMs must be >= 0")
        @Max(value = 10000, message = "addedLatencyMs must be <= 10000")
        int addedLatencyMs,

        @Min(value = 0, message = "addedJitterMs must be >= 0")
        @Max(value = 5000, message = "addedJitterMs must be <= 5000")
        int addedJitterMs,

        @DecimalMin(value = "0.0", message = "packetDropPct must be >= 0.0")
        @DecimalMax(value = "100.0", message = "packetDropPct must be <= 100.0")
        double packetDropPct,

        boolean forceDnsFailure,

        boolean forceTimeout,

        Boolean active
) {
    public boolean resolvedActive() {
        return active == null || active;
    }
}

package com.netpulse.dto;

public record OverviewResponse(
        long totalTargets,
        long activeTargets,
        long healthyTargets,
        long degradedTargets,
        long downTargets,
        long pausedTargets,
        long unknownTargets,
        Double averageLatencyMs,
        long openIncidents,
        long activeChaosRules
) {
}

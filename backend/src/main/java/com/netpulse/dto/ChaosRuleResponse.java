package com.netpulse.dto;

import com.netpulse.domain.ChaosRule;

import java.time.Instant;

public record ChaosRuleResponse(
        Long id,
        Long targetId,
        String targetName,
        int addedLatencyMs,
        int addedJitterMs,
        double packetDropPct,
        boolean forceDnsFailure,
        boolean forceTimeout,
        boolean active,
        Instant updatedAt
) {
    public static ChaosRuleResponse from(ChaosRule rule) {
        return new ChaosRuleResponse(
                rule.getId(),
                rule.getTarget().getId(),
                rule.getTarget().getName(),
                rule.getAddedLatencyMs(),
                rule.getAddedJitterMs(),
                rule.getPacketDropPct(),
                rule.isForceDnsFailure(),
                rule.isForceTimeout(),
                rule.isActive(),
                rule.getUpdatedAt()
        );
    }
}

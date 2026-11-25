package com.netpulse.dto;

import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProtocolType;
import com.netpulse.domain.TargetStatus;

import java.time.Instant;

public record TargetResponse(
        Long id,
        String name,
        String host,
        Integer port,
        String path,
        ProtocolType protocol,
        Integer intervalSeconds,
        Integer timeoutMs,
        boolean enabled,
        TargetStatus status,
        Instant lastCheckedAt,
        Double lastTotalLatencyMs,
        Double lastPacketLossPct,
        int consecutiveFailures,
        int consecutiveHealthy,
        boolean chaosActive,
        long openIncidentCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static TargetResponse from(MonitorTarget target, boolean chaosActive, long openIncidentCount) {
        return new TargetResponse(
                target.getId(),
                target.getName(),
                target.getHost(),
                target.getPort(),
                target.getPath(),
                target.getProtocol(),
                target.getIntervalSeconds(),
                target.getTimeoutMs(),
                target.isEnabled(),
                target.getStatus(),
                target.getLastCheckedAt(),
                target.getLastTotalLatencyMs(),
                target.getLastPacketLossPct(),
                target.getConsecutiveFailures(),
                target.getConsecutiveHealthy(),
                chaosActive,
                openIncidentCount,
                target.getCreatedAt(),
                target.getUpdatedAt()
        );
    }
}

package com.netpulse.dto;

import com.netpulse.domain.Incident;
import com.netpulse.domain.IncidentSeverity;
import com.netpulse.domain.IncidentStatus;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        Long targetId,
        String targetName,
        IncidentSeverity severity,
        IncidentStatus status,
        String triggerType,
        String summary,
        String rootCause,
        Double peakLatencyMs,
        Double peakPacketLossPct,
        Double peakZScore,
        Instant openedAt,
        Instant resolvedAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTarget().getId(),
                incident.getTarget().getName(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getTriggerType(),
                incident.getSummary(),
                incident.getRootCause(),
                incident.getPeakLatencyMs(),
                incident.getPeakPacketLossPct(),
                incident.getPeakZScore(),
                incident.getOpenedAt(),
                incident.getResolvedAt()
        );
    }
}

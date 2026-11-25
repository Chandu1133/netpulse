package com.netpulse.dto;

import com.netpulse.domain.ProbeResult;

import java.time.Instant;

public record ProbeResultResponse(
        Long id,
        Long targetId,
        String targetName,
        Instant checkedAt,
        boolean success,
        String resolvedIp,
        Double dnsTimeMs,
        Double tcpConnectMs,
        Double tlsHandshakeMs,
        Double ttfbMs,
        Double totalLatencyMs,
        Integer httpStatusCode,
        Integer tlsCertDaysRemaining,
        Double packetLossPct,
        Double jitterMs,
        Double rollingMeanMs,
        Double rollingStdDevMs,
        Double zScore,
        boolean chaosInjected,
        String errorMessage
) {
    public static ProbeResultResponse from(ProbeResult probe) {
        return new ProbeResultResponse(
                probe.getId(),
                probe.getTarget().getId(),
                probe.getTarget().getName(),
                probe.getCheckedAt(),
                probe.isSuccess(),
                probe.getResolvedIp(),
                probe.getDnsTimeMs(),
                probe.getTcpConnectMs(),
                probe.getTlsHandshakeMs(),
                probe.getTtfbMs(),
                probe.getTotalLatencyMs(),
                probe.getHttpStatusCode(),
                probe.getTlsCertDaysRemaining(),
                probe.getPacketLossPct(),
                probe.getJitterMs(),
                probe.getRollingMeanMs(),
                probe.getRollingStdDevMs(),
                probe.getZScore(),
                probe.isChaosInjected(),
                probe.getErrorMessage()
        );
    }
}

package com.netpulse.prober;

import java.time.Instant;

public record ProbeExecutionResult(
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
        boolean chaosInjected,
        String errorMessage
) {
    public static ProbeExecutionResult failure(Instant checkedAt,
                                               String resolvedIp,
                                               Double dnsTimeMs,
                                               Double tcpConnectMs,
                                               Double tlsHandshakeMs,
                                               Double totalLatencyMs,
                                               Double packetLossPct,
                                               Double jitterMs,
                                               boolean chaosInjected,
                                               String errorMessage) {
        return new ProbeExecutionResult(
                checkedAt,
                false,
                resolvedIp,
                dnsTimeMs,
                tcpConnectMs,
                tlsHandshakeMs,
                null,
                totalLatencyMs,
                null,
                null,
                packetLossPct,
                jitterMs,
                chaosInjected,
                errorMessage
        );
    }
}

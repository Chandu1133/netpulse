package com.netpulse.websocket;

import java.time.Instant;

public record TelemetryEvent(
        String type,
        Instant timestamp,
        Object payload
) {
    public static TelemetryEvent of(String type, Object payload) {
        return new TelemetryEvent(type, Instant.now(), payload);
    }
}

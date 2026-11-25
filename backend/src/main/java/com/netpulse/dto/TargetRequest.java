package com.netpulse.dto;

import com.netpulse.domain.ProtocolType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TargetRequest(
        @NotBlank(message = "Target name is required")
        @Size(max = 120, message = "Target name must be at most 120 characters")
        String name,

        @NotBlank(message = "Host is required")
        @Size(max = 255, message = "Host must be at most 255 characters")
        String host,

        @Min(value = 1, message = "Port must be between 1 and 65535")
        @Max(value = 65535, message = "Port must be between 1 and 65535")
        Integer port,

        @Size(max = 255, message = "Path must be at most 255 characters")
        String path,

        @NotNull(message = "Protocol is required")
        ProtocolType protocol,

        @Min(value = 5, message = "Interval must be at least 5 seconds")
        @Max(value = 3600, message = "Interval must be at most 3600 seconds")
        Integer intervalSeconds,

        @Min(value = 200, message = "Timeout must be at least 200ms")
        @Max(value = 30000, message = "Timeout must be at most 30000ms")
        Integer timeoutMs,

        Boolean enabled
) {
    public int resolvedPort() {
        if (port != null) {
            return port;
        }
        return switch (protocol) {
            case HTTPS, TCP -> 443;
            case HTTP -> 80;
            case DNS -> 53;
        };
    }

    public int resolvedIntervalSeconds() {
        return intervalSeconds != null ? intervalSeconds : 10;
    }

    public int resolvedTimeoutMs() {
        return timeoutMs != null ? timeoutMs : 3000;
    }

    public boolean resolvedEnabled() {
        return enabled == null || enabled;
    }
}

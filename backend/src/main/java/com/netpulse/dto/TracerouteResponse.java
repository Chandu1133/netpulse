package com.netpulse.dto;

import java.time.Instant;
import java.util.List;

public record TracerouteResponse(
        Long targetId,
        String targetName,
        String host,
        String resolvedIp,
        boolean simulated,
        String osPlatform,
        Instant executedAt,
        List<HopDto> hops
) {
}

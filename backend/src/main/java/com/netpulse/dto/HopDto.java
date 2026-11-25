package com.netpulse.dto;

public record HopDto(
        int hopNumber,
        String ip,
        String hostname,
        Double rttMs,
        boolean timedOut,
        String status
) {
}

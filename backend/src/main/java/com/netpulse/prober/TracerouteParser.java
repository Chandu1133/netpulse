package com.netpulse.prober;

import com.netpulse.dto.HopDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TracerouteParser {

    private static final double SLOW_HOP_THRESHOLD_MS = 100.0;
    private static final Pattern HOP_PREFIX = Pattern.compile("^\\s*(\\d+)\\s+(.*)$");
    private static final Pattern IPV4_PATTERN = Pattern.compile("\\b((?:\\d{1,3}\\.){3}\\d{1,3})\\b");
    private static final Pattern WINDOWS_RTT_PATTERN = Pattern.compile("(?:<\\s*(\\d+)|(\\d+(?:\\.\\d+)?))\\s*ms");

    public List<HopDto> parseWindowsTracert(List<String> lines) {
        List<HopDto> hops = new ArrayList<>();
        if (lines == null) {
            return hops;
        }

        for (String line : lines) {
            Matcher prefix = HOP_PREFIX.matcher(line);
            if (!prefix.matches()) {
                continue;
            }
            int hopNum = Integer.parseInt(prefix.group(1));
            String rest = prefix.group(2).trim();

            if (rest.toLowerCase().contains("request timed out") || rest.matches("^\\*\\s+\\*\\s+\\*.*$")) {
                hops.add(new HopDto(hopNum, "*", null, null, true, "TIMEOUT"));
                continue;
            }

            List<Double> rtts = extractRtts(rest);
            String ip = extractIp(rest);
            if (ip == null && rtts.isEmpty()) {
                continue;
            }

            Double avgRtt = averageRtt(rtts);
            boolean timedOut = ip == null && avgRtt == null;
            hops.add(new HopDto(
                    hopNum,
                    ip != null ? ip : "*",
                    null,
                    avgRtt,
                    timedOut,
                    classifyStatus(avgRtt, timedOut)
            ));
        }

        return hops;
    }

    public List<HopDto> parseLinuxTraceroute(List<String> lines) {
        List<HopDto> hops = new ArrayList<>();
        if (lines == null) {
            return hops;
        }

        for (String line : lines) {
            Matcher prefix = HOP_PREFIX.matcher(line);
            if (!prefix.matches()) {
                continue;
            }
            int hopNum = Integer.parseInt(prefix.group(1));
            String rest = prefix.group(2).trim();

            if (rest.matches("^\\*(?:\\s+\\*)*$")) {
                hops.add(new HopDto(hopNum, "*", null, null, true, "TIMEOUT"));
                continue;
            }

            String ip = extractIp(rest);
            List<Double> rtts = extractRtts(rest);
            Double avgRtt = averageRtt(rtts);
            boolean timedOut = ip == null && avgRtt == null;

            hops.add(new HopDto(
                    hopNum,
                    ip != null ? ip : "*",
                    null,
                    avgRtt,
                    timedOut,
                    classifyStatus(avgRtt, timedOut)
            ));
        }

        return hops;
    }

    public List<HopDto> generateSimulatedPath(String host, String resolvedIp, int extraChaosLatencyMs) {
        int seed = Math.abs((host == null ? "localhost" : host).hashCode());
        String destIp = (resolvedIp != null && !resolvedIp.isBlank())
                ? resolvedIp
                : "104." + (16 + (seed % 40)) + "." + (10 + (seed % 200)) + "." + (20 + (seed % 200));

        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
            double localRtt = round2(0.4 + extraChaosLatencyMs);
            return List.of(
                    new HopDto(1, "127.0.0.1", "loopback.local", localRtt, false, classifyStatus(localRtt, false))
            );
        }

        List<HopDto> hops = new ArrayList<>();
        double cumulative = 1.2;

        hops.add(new HopDto(1, "192.168.1.1", "local-gateway", round2(cumulative), false, "NORMAL"));

        cumulative += 4.5 + (seed % 4);
        hops.add(new HopDto(2, "10." + (10 + seed % 50) + ".0.1", "isp-edge-gw", round2(cumulative), false, "NORMAL"));

        // Deterministic intermediate transit hop (some paths include a filtered ICMP hop)
        if (seed % 3 == 0) {
            hops.add(new HopDto(3, "*", null, null, true, "TIMEOUT"));
        } else {
            cumulative += 6.2 + (seed % 7);
            hops.add(new HopDto(3, "172.16." + (20 + seed % 80) + ".14", "metro-core-rtr", round2(cumulative), false, "NORMAL"));
        }

        cumulative += 9.8 + (seed % 11) + (extraChaosLatencyMs * 0.4);
        String ixIp = "198.32." + (100 + seed % 50) + "." + (10 + seed % 90);
        hops.add(new HopDto(4, ixIp, "ixp-peering-01", round2(cumulative), false, classifyStatus(cumulative, false)));

        cumulative += 7.5 + (seed % 9) + (extraChaosLatencyMs * 0.6);
        hops.add(new HopDto(5, destIp, host, round2(cumulative), false, classifyStatus(cumulative, false)));

        return hops;
    }

    private List<Double> extractRtts(String text) {
        List<Double> rtts = new ArrayList<>();
        Matcher matcher = WINDOWS_RTT_PATTERN.matcher(text);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                rtts.add(0.5); // "<1 ms"
            } else if (matcher.group(2) != null) {
                rtts.add(Double.parseDouble(matcher.group(2)));
            }
        }
        return rtts;
    }

    private String extractIp(String text) {
        Matcher matcher = IPV4_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Double averageRtt(List<Double> rtts) {
        if (rtts.isEmpty()) {
            return null;
        }
        double avg = rtts.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return round2(avg);
    }

    private String classifyStatus(Double rttMs, boolean timedOut) {
        if (timedOut || rttMs == null) {
            return "TIMEOUT";
        }
        return rttMs >= SLOW_HOP_THRESHOLD_MS ? "SLOW" : "NORMAL";
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

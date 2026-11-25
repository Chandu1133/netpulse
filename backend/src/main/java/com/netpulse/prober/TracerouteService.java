package com.netpulse.prober;

import com.netpulse.config.NetPulseProperties;
import com.netpulse.domain.ChaosRule;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.dto.HopDto;
import com.netpulse.dto.TracerouteResponse;
import com.netpulse.exception.ResourceNotFoundException;
import com.netpulse.repository.ChaosRuleRepository;
import com.netpulse.repository.MonitorTargetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class TracerouteService {

    private static final Logger log = LoggerFactory.getLogger(TracerouteService.class);

    private final MonitorTargetRepository targetRepository;
    private final ChaosRuleRepository chaosRuleRepository;
    private final TracerouteParser parser;
    private final NetPulseProperties properties;

    public TracerouteService(MonitorTargetRepository targetRepository,
                             ChaosRuleRepository chaosRuleRepository,
                             TracerouteParser parser,
                             NetPulseProperties properties) {
        this.targetRepository = targetRepository;
        this.chaosRuleRepository = chaosRuleRepository;
        this.parser = parser;
        this.properties = properties;
    }

    public TracerouteResponse traceTarget(Long targetId, boolean forceSimulated) {
        MonitorTarget target = targetRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitor target not found: " + targetId));

        String osName = System.getProperty("os.name", "unknown");
        boolean isWindows = osName.toLowerCase(Locale.ROOT).contains("win");

        int extraChaosMs = chaosRuleRepository.findByTargetId(targetId)
                .filter(ChaosRule::hasAnyFault)
                .map(ChaosRule::getAddedLatencyMs)
                .orElse(0);

        String resolvedIp = resolveIpSafely(target.getHost());
        Instant executedAt = Instant.now();

        if (forceSimulated || "localhost".equalsIgnoreCase(target.getHost()) || "127.0.0.1".equals(target.getHost())) {
            List<HopDto> simulatedHops = parser.generateSimulatedPath(target.getHost(), resolvedIp, extraChaosMs);
            return new TracerouteResponse(
                    target.getId(),
                    target.getName(),
                    target.getHost(),
                    resolvedIp,
                    true,
                    osName,
                    executedAt,
                    simulatedHops
            );
        }

        List<HopDto> realHops = executeOsTraceroute(target.getHost(), isWindows);
        if (!realHops.isEmpty()) {
            return new TracerouteResponse(
                    target.getId(),
                    target.getName(),
                    target.getHost(),
                    resolvedIp,
                    false,
                    osName,
                    executedAt,
                    realHops
            );
        }

        // Deterministic fallback for offline demos or restricted ICMP environments
        List<HopDto> fallbackHops = parser.generateSimulatedPath(target.getHost(), resolvedIp, extraChaosMs);
        return new TracerouteResponse(
                target.getId(),
                target.getName(),
                target.getHost(),
                resolvedIp,
                true,
                osName,
                executedAt,
                fallbackHops
        );
    }

    private List<HopDto> executeOsTraceroute(String host, boolean isWindows) {
        int maxHops = properties.getProber().getTracerouteMaxHops();
        int timeoutSeconds = properties.getProber().getTracerouteTimeoutSeconds();

        // Sanitize host against command injection before passing to ProcessBuilder
        if (host == null || !host.matches("^[a-zA-Z0-9._:-]+$")) {
            return List.of();
        }

        List<String> command = isWindows
                ? List.of("tracert", "-d", "-h", String.valueOf(maxHops), "-w", "1000", host)
                : List.of("traceroute", "-n", "-m", String.valueOf(maxHops), "-w", "1", host);

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            List<String> lines = new ArrayList<>();
            Process startedProcess = process;
            Thread readerThread = Thread.ofVirtual().start(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(startedProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                } catch (Exception ignored) {
                    // Process may be destroyed on timeout
                }
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            readerThread.join(1000);

            return isWindows
                    ? parser.parseWindowsTracert(lines)
                    : parser.parseLinuxTraceroute(lines);
        } catch (Exception ex) {
            log.debug("OS traceroute failed for {}, falling back to simulated path: {}", host, ex.getMessage());
            if (process != null) {
                process.destroyForcibly();
            }
            return List.of();
        }
    }

    private String resolveIpSafely(String host) {
        try {
            return InetAddress.getByName(host).getHostAddress();
        } catch (Exception ex) {
            return null;
        }
    }
}

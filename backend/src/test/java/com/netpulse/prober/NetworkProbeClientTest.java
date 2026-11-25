package com.netpulse.prober;

import com.netpulse.config.NetPulseProperties;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProtocolType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkProbeClientTest {

    private HttpServer httpServer;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        httpServer.start();
        port = httpServer.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void probesLocalHttpServerMeasuringDnsTcpTtfbAndZeroLoss() {
        DefaultNetworkProbeClient client = new DefaultNetworkProbeClient(new NetPulseProperties());
        MonitorTarget target = new MonitorTarget("Local Test", "127.0.0.1", port, "/health",
                ProtocolType.HTTP, 10, 2000);

        ProbeExecutionResult result = client.executeProbe(target);

        assertThat(result.success()).isTrue();
        assertThat(result.resolvedIp()).isEqualTo("127.0.0.1");
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.dnsTimeMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.tcpConnectMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.ttfbMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.totalLatencyMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.packetLossPct()).isEqualTo(0.0);
        assertThat(result.jitterMs()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void probesLocalTcpPortSuccessfully() {
        DefaultNetworkProbeClient client = new DefaultNetworkProbeClient(new NetPulseProperties());
        MonitorTarget target = new MonitorTarget("Local TCP", "127.0.0.1", port, null,
                ProtocolType.TCP, 10, 2000);

        ProbeExecutionResult result = client.executeProbe(target);

        assertThat(result.success()).isTrue();
        assertThat(result.tcpConnectMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.packetLossPct()).isEqualTo(0.0);
    }

    @Test
    void computesJitterAsMeanAbsoluteConsecutiveDifference() {
        assertThat(DefaultNetworkProbeClient.computeJitter(List.of(10.0, 14.0, 12.0))).isEqualTo(3.0);
        assertThat(DefaultNetworkProbeClient.computeJitter(List.of(10.0))).isEqualTo(0.0);
    }
}

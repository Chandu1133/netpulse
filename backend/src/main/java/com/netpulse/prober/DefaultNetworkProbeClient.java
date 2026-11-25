package com.netpulse.prober;

import com.netpulse.config.NetPulseProperties;
import com.netpulse.domain.MonitorTarget;
import com.netpulse.domain.ProtocolType;
import org.springframework.stereotype.Component;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class DefaultNetworkProbeClient implements NetworkProbeClient {

    private static final double NANOS_PER_MS = 1_000_000.0;

    private final NetPulseProperties properties;

    public DefaultNetworkProbeClient(NetPulseProperties properties) {
        this.properties = properties;
    }

    @Override
    public ProbeExecutionResult executeProbe(MonitorTarget target) {
        Instant checkedAt = Instant.now();
        int timeoutMs = target.getTimeoutMs() != null
                ? target.getTimeoutMs()
                : properties.getProber().getDefaultTimeoutMs();
        int attempts = Math.max(1, properties.getProber().getPacketProbeAttempts());

        // 1. DNS Resolution
        long dnsStart = System.nanoTime();
        InetAddress resolvedAddress;
        try {
            InetAddress[] addresses = InetAddress.getAllByName(target.getHost());
            resolvedAddress = pickPreferredAddress(addresses);
        } catch (UnknownHostException ex) {
            double dnsMs = elapsedMs(dnsStart);
            return ProbeExecutionResult.failure(
                    checkedAt,
                    null,
                    dnsMs,
                    null,
                    null,
                    dnsMs,
                    100.0,
                    0.0,
                    false,
                    "DNS resolution failed: " + ex.getMessage()
            );
        }
        double dnsTimeMs = elapsedMs(dnsStart);
        String resolvedIp = resolvedAddress.getHostAddress();

        // 2. Protocol-specific primary probe
        return switch (target.getProtocol()) {
            case HTTPS -> probeHttp(target, checkedAt, resolvedAddress, resolvedIp, dnsTimeMs, timeoutMs, attempts, true);
            case HTTP -> probeHttp(target, checkedAt, resolvedAddress, resolvedIp, dnsTimeMs, timeoutMs, attempts, false);
            case TCP -> probeTcp(target, checkedAt, resolvedAddress, resolvedIp, dnsTimeMs, timeoutMs, attempts);
            case DNS -> probeDns(target, checkedAt, resolvedAddress, resolvedIp, dnsTimeMs, timeoutMs, attempts);
        };
    }

    private ProbeExecutionResult probeHttp(MonitorTarget target,
                                           Instant checkedAt,
                                           InetAddress address,
                                           String resolvedIp,
                                           double dnsTimeMs,
                                           int timeoutMs,
                                           int attempts,
                                           boolean useTls) {
        int port = target.getPort() != null ? target.getPort() : (useTls ? 443 : 80);
        String path = (target.getPath() == null || target.getPath().isBlank()) ? "/" : target.getPath();

        Double tcpConnectMs = null;
        Double tlsHandshakeMs = null;
        Double ttfbMs = null;
        Integer httpStatusCode = null;
        Integer tlsCertDaysRemaining = null;

        try (Socket rawSocket = new Socket()) {
            long tcpStart = System.nanoTime();
            rawSocket.connect(new InetSocketAddress(address, port), timeoutMs);
            tcpConnectMs = elapsedMs(tcpStart);
            rawSocket.setSoTimeout(timeoutMs);

            Socket activeSocket = rawSocket;
            if (useTls) {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) factory.createSocket(rawSocket, target.getHost(), port, true);
                SSLParameters sslParams = sslSocket.getSSLParameters();
                if (!isIpLiteral(target.getHost())) {
                    sslParams.setServerNames(List.of(new SNIHostName(target.getHost())));
                    sslParams.setEndpointIdentificationAlgorithm("HTTPS");
                }
                sslSocket.setSSLParameters(sslParams);
                sslSocket.setSoTimeout(timeoutMs);

                long tlsStart = System.nanoTime();
                sslSocket.startHandshake();
                tlsHandshakeMs = elapsedMs(tlsStart);
                tlsCertDaysRemaining = extractCertDaysRemaining(sslSocket.getSession().getPeerCertificates());
                activeSocket = sslSocket;
            }

            String hostHeader = (port == 80 || port == 443) ? target.getHost() : target.getHost() + ":" + port;
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader + "\r\n"
                    + "User-Agent: NetPulse/1.0\r\n"
                    + "Accept: */*\r\n"
                    + "Connection: close\r\n\r\n";

            long ttfbStart = System.nanoTime();
            OutputStream out = activeSocket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            InputStream in = activeSocket.getInputStream();
            int firstByte = in.read();
            ttfbMs = elapsedMs(ttfbStart);

            if (firstByte == -1) {
                throw new IOException("Connection closed before HTTP response");
            }

            String restOfStatusLine = readLine(in);
            String statusLine = (char) firstByte + restOfStatusLine;
            httpStatusCode = parseHttpStatusCode(statusLine);

            PacketStats packetStats = measureTcpPacketStats(address, port, Math.min(timeoutMs, 1500), attempts, tcpConnectMs);
            double totalLatency = round2(dnsTimeMs + tcpConnectMs + (tlsHandshakeMs != null ? tlsHandshakeMs : 0.0) + ttfbMs);
            boolean httpOk = httpStatusCode != null && httpStatusCode >= 200 && httpStatusCode < 500;
            String error = httpOk ? null : "HTTP status " + (httpStatusCode != null ? httpStatusCode : "unknown");

            return new ProbeExecutionResult(
                    checkedAt,
                    httpOk,
                    resolvedIp,
                    dnsTimeMs,
                    tcpConnectMs,
                    tlsHandshakeMs,
                    ttfbMs,
                    totalLatency,
                    httpStatusCode,
                    tlsCertDaysRemaining,
                    packetStats.packetLossPct(),
                    packetStats.jitterMs(),
                    false,
                    error
            );
        } catch (Exception ex) {
            PacketStats packetStats = measureTcpPacketStats(address, port, Math.min(timeoutMs, 1000), attempts, null);
            double partialTotal = round2(dnsTimeMs
                    + (tcpConnectMs != null ? tcpConnectMs : 0.0)
                    + (tlsHandshakeMs != null ? tlsHandshakeMs : 0.0));
            return ProbeExecutionResult.failure(
                    checkedAt,
                    resolvedIp,
                    dnsTimeMs,
                    tcpConnectMs,
                    tlsHandshakeMs,
                    partialTotal,
                    packetStats.packetLossPct(),
                    packetStats.jitterMs(),
                    false,
                    truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 500)
            );
        }
    }

    private ProbeExecutionResult probeTcp(MonitorTarget target,
                                          Instant checkedAt,
                                          InetAddress address,
                                          String resolvedIp,
                                          double dnsTimeMs,
                                          int timeoutMs,
                                          int attempts) {
        int port = target.getPort() != null ? target.getPort() : 443;
        PacketStats stats = measureTcpPacketStats(address, port, timeoutMs, attempts, null);

        if (stats.successfulRtts().isEmpty()) {
            return ProbeExecutionResult.failure(
                    checkedAt,
                    resolvedIp,
                    dnsTimeMs,
                    null,
                    null,
                    round2(dnsTimeMs + timeoutMs),
                    100.0,
                    0.0,
                    false,
                    stats.lastError() != null ? stats.lastError() : "TCP connect failed on port " + port
            );
        }

        double avgTcpMs = round2(stats.successfulRtts().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0));
        double totalLatency = round2(dnsTimeMs + avgTcpMs);

        return new ProbeExecutionResult(
                checkedAt,
                true,
                resolvedIp,
                dnsTimeMs,
                avgTcpMs,
                null,
                null,
                totalLatency,
                null,
                null,
                stats.packetLossPct(),
                stats.jitterMs(),
                false,
                null
        );
    }

    private ProbeExecutionResult probeDns(MonitorTarget target,
                                          Instant checkedAt,
                                          InetAddress address,
                                          String resolvedIp,
                                          double dnsTimeMs,
                                          int timeoutMs,
                                          int attempts) {
        int port = target.getPort() != null ? target.getPort() : 53;
        List<Double> rtts = new ArrayList<>();
        String lastError = null;

        for (int i = 0; i < attempts; i++) {
            try {
                double rtt = sendUdpDnsQuery(address, port, Math.min(timeoutMs, 1500));
                rtts.add(rtt);
            } catch (Exception udpEx) {
                lastError = "DNS UDP query failed: " + udpEx.getMessage();
                // Fallback to TCP port 53 connect if UDP is blocked by local firewall
                try {
                    double tcpRtt = singleTcpConnect(address, port, Math.min(timeoutMs, 1500));
                    rtts.add(tcpRtt);
                    lastError = null;
                } catch (Exception tcpEx) {
                    lastError = "DNS query failed: " + udpEx.getMessage();
                }
            }
        }

        double packetLossPct = round2(((attempts - rtts.size()) / (double) attempts) * 100.0);
        double jitterMs = computeJitter(rtts);

        if (rtts.isEmpty()) {
            return ProbeExecutionResult.failure(
                    checkedAt,
                    resolvedIp,
                    dnsTimeMs,
                    null,
                    null,
                    round2(dnsTimeMs + timeoutMs),
                    100.0,
                    0.0,
                    false,
                    truncate(lastError, 500)
            );
        }

        double avgQueryMs = round2(rtts.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        double totalLatency = round2(dnsTimeMs + avgQueryMs);

        return new ProbeExecutionResult(
                checkedAt,
                true,
                resolvedIp,
                avgQueryMs,
                null,
                null,
                null,
                totalLatency,
                null,
                null,
                packetLossPct,
                jitterMs,
                false,
                null
        );
    }

    private PacketStats measureTcpPacketStats(InetAddress address,
                                              int port,
                                              int timeoutMs,
                                              int attempts,
                                              Double initialSampleMs) {
        List<Double> rtts = new ArrayList<>();
        int remaining = attempts;
        if (initialSampleMs != null) {
            rtts.add(initialSampleMs);
            remaining = Math.max(0, attempts - 1);
        }

        String lastError = null;
        for (int i = 0; i < remaining; i++) {
            try {
                rtts.add(singleTcpConnect(address, port, timeoutMs));
            } catch (Exception ex) {
                lastError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
        }

        double lossPct = round2(((attempts - rtts.size()) / (double) attempts) * 100.0);
        double jitterMs = computeJitter(rtts);
        return new PacketStats(rtts, lossPct, jitterMs, lastError);
    }

    private double singleTcpConnect(InetAddress address, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            long start = System.nanoTime();
            socket.connect(new InetSocketAddress(address, port), timeoutMs);
            return elapsedMs(start);
        }
    }

    private double sendUdpDnsQuery(InetAddress server, int port, int timeoutMs) throws IOException {
        int txId = ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF);
        byte[] query = buildDnsAQuery(txId, "cloudflare.com");

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            DatagramPacket requestPacket = new DatagramPacket(query, query.length, server, port);
            byte[] responseBuf = new byte[512];
            DatagramPacket responsePacket = new DatagramPacket(responseBuf, responseBuf.length);

            long start = System.nanoTime();
            socket.send(requestPacket);
            socket.receive(responsePacket);
            double rtt = elapsedMs(start);

            if (responsePacket.getLength() < 12) {
                throw new IOException("Malformed DNS response (length < 12)");
            }
            int respTxId = ((responseBuf[0] & 0xFF) << 8) | (responseBuf[1] & 0xFF);
            if (respTxId != txId) {
                throw new IOException("DNS transaction ID mismatch");
            }
            return rtt;
        }
    }

    private byte[] buildDnsAQuery(int txId, String domain) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeShort(txId);     // Transaction ID
        dos.writeShort(0x0100);   // Standard query, recursion desired
        dos.writeShort(1);        // QDCOUNT = 1
        dos.writeShort(0);        // ANCOUNT = 0
        dos.writeShort(0);        // NSCOUNT = 0
        dos.writeShort(0);        // ARCOUNT = 0

        for (String label : domain.split("\\.")) {
            byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
            dos.writeByte(labelBytes.length);
            dos.write(labelBytes);
        }
        dos.writeByte(0);         // End of QNAME
        dos.writeShort(1);        // QTYPE = A
        dos.writeShort(1);        // QCLASS = IN
        dos.flush();
        return baos.toByteArray();
    }

    static double computeJitter(List<Double> rtts) {
        if (rtts == null || rtts.size() < 2) {
            return 0.0;
        }
        double sumDiffs = 0.0;
        for (int i = 1; i < rtts.size(); i++) {
            sumDiffs += Math.abs(rtts.get(i) - rtts.get(i - 1));
        }
        return round2(sumDiffs / (rtts.size() - 1));
    }

    private Integer extractCertDaysRemaining(Certificate[] certs) {
        if (certs == null || certs.length == 0) {
            return null;
        }
        if (certs[0] instanceof X509Certificate x509) {
            Instant expiry = x509.getNotAfter().toInstant();
            long days = Duration.between(Instant.now(), expiry).toDays();
            return (int) days;
        }
        return null;
    }

    private InetAddress pickPreferredAddress(InetAddress[] addresses) {
        for (InetAddress addr : addresses) {
            if (addr instanceof Inet4Address) {
                return addr;
            }
        }
        return addresses[0];
    }

    private String readLine(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
        String line = reader.readLine();
        return line != null ? line : "";
    }

    private Integer parseHttpStatusCode(String statusLine) {
        if (statusLine == null) {
            return null;
        }
        String[] parts = statusLine.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isIpLiteral(String host) {
        return host != null && (host.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$") || host.contains(":"));
    }

    private double elapsedMs(long startNanos) {
        return round2((System.nanoTime() - startNanos) / NANOS_PER_MS);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String truncate(String msg, int maxLen) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= maxLen ? msg : msg.substring(0, maxLen);
    }

    private record PacketStats(List<Double> successfulRtts, double packetLossPct, double jitterMs, String lastError) {
    }
}

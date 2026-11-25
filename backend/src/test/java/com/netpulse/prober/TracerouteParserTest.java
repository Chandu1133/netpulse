package com.netpulse.prober;

import com.netpulse.dto.HopDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TracerouteParserTest {

    private final TracerouteParser parser = new TracerouteParser();

    @Test
    void parsesWindowsTracertOutputIncludingSubMillisecondAndTimeouts() {
        List<String> output = List.of(
                "Tracing route to 1.1.1.1 over a maximum of 12 hops",
                "",
                "  1    <1 ms    <1 ms     1 ms  192.168.1.1",
                "  2     *        *        *     Request timed out.",
                "  3    14 ms    16 ms    15 ms  10.20.30.1",
                "  4   120 ms   130 ms   125 ms  1.1.1.1",
                "Trace complete."
        );

        List<HopDto> hops = parser.parseWindowsTracert(output);

        assertThat(hops).hasSize(4);

        assertThat(hops.get(0).hopNumber()).isEqualTo(1);
        assertThat(hops.get(0).ip()).isEqualTo("192.168.1.1");
        assertThat(hops.get(0).rttMs()).isEqualTo(0.67);
        assertThat(hops.get(0).status()).isEqualTo("NORMAL");

        assertThat(hops.get(1).hopNumber()).isEqualTo(2);
        assertThat(hops.get(1).timedOut()).isTrue();
        assertThat(hops.get(1).status()).isEqualTo("TIMEOUT");

        assertThat(hops.get(2).hopNumber()).isEqualTo(3);
        assertThat(hops.get(2).ip()).isEqualTo("10.20.30.1");
        assertThat(hops.get(2).rttMs()).isEqualTo(15.0);

        assertThat(hops.get(3).hopNumber()).isEqualTo(4);
        assertThat(hops.get(3).ip()).isEqualTo("1.1.1.1");
        assertThat(hops.get(3).rttMs()).isEqualTo(125.0);
        assertThat(hops.get(3).status()).isEqualTo("SLOW");
    }

    @Test
    void parsesLinuxTracerouteOutput() {
        List<String> output = List.of(
                "traceroute to cloudflare.com (104.16.132.229), 12 hops max, 60 byte packets",
                " 1  192.168.1.1  0.80 ms  0.90 ms  1.00 ms",
                " 2  * * *",
                " 3  104.16.132.229  12.40 ms  12.60 ms  12.50 ms"
        );

        List<HopDto> hops = parser.parseLinuxTraceroute(output);

        assertThat(hops).hasSize(3);
        assertThat(hops.get(0).ip()).isEqualTo("192.168.1.1");
        assertThat(hops.get(0).rttMs()).isEqualTo(0.9);
        assertThat(hops.get(1).timedOut()).isTrue();
        assertThat(hops.get(2).ip()).isEqualTo("104.16.132.229");
        assertThat(hops.get(2).rttMs()).isEqualTo(12.5);
    }

    @Test
    void generatesDeterministicSimulatedPathWithChaosLatency() {
        List<HopDto> first = parser.generateSimulatedPath("cloudflare.com", "104.16.132.229", 300);
        List<HopDto> second = parser.generateSimulatedPath("cloudflare.com", "104.16.132.229", 300);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(5);
        assertThat(first.getLast().ip()).isEqualTo("104.16.132.229");
        assertThat(first.getLast().status()).isEqualTo("SLOW");
    }
}

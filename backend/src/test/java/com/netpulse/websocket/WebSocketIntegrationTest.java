package com.netpulse.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TelemetryWebSocketHandler handler;

    @Test
    void connectsAndReceivesRealTimeBroadcastEvents() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        StandardWebSocketClient client = new StandardWebSocketClient();

        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.offer(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws/telemetry").get(5, TimeUnit.SECONDS);

        try {
            String welcome = received.poll(5, TimeUnit.SECONDS);
            assertThat(welcome).isNotNull().contains("\"type\":\"CONNECTED\"");

            handler.broadcast(TelemetryEvent.of("INCIDENT_OPENED", Map.of("id", 99, "severity", "CRITICAL")));

            String broadcast = received.poll(5, TimeUnit.SECONDS);
            assertThat(broadcast).isNotNull()
                    .contains("\"type\":\"INCIDENT_OPENED\"")
                    .contains("\"severity\":\"CRITICAL\"");
        } finally {
            session.close();
        }
    }
}

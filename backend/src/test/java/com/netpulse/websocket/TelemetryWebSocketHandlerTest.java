package com.netpulse.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetryWebSocketHandlerTest {

    @Test
    void broadcastsSerializedEventsToOpenSessionsAndPrunesClosedSessions() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TelemetryWebSocketHandler handler = new TelemetryWebSocketHandler(mapper);

        WebSocketSession openSession = mock(WebSocketSession.class);
        when(openSession.getId()).thenReturn("sess-1");
        when(openSession.isOpen()).thenReturn(true);

        WebSocketSession closedSession = mock(WebSocketSession.class);
        when(closedSession.getId()).thenReturn("sess-2");
        when(closedSession.isOpen()).thenReturn(true, false);

        handler.afterConnectionEstablished(openSession);
        handler.afterConnectionEstablished(closedSession);
        assertThat(handler.getActiveSessionCount()).isEqualTo(2);

        handler.broadcast(TelemetryEvent.of("PROBE_RESULT", Map.of("targetId", 1, "totalLatencyMs", 42.5)));

        // closedSession was pruned on broadcast because isOpen() returned false
        assertThat(handler.getActiveSessionCount()).isEqualTo(1);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(openSession, times(2)).sendMessage(captor.capture());

        String payload = captor.getAllValues().get(1).getPayload();
        assertThat(payload).contains("\"type\":\"PROBE_RESULT\"");
        assertThat(payload).contains("\"totalLatencyMs\":42.5");
    }
}

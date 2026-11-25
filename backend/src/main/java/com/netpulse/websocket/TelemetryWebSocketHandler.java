package com.netpulse.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetryWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public TelemetryWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.debug("WebSocket client connected: {} (active={})", session.getId(), sessions.size());
        sendToSession(session, TelemetryEvent.of("CONNECTED", Map.of(
                "sessionId", session.getId(),
                "message", "Connected to NetPulse live telemetry stream"
        )));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.debug("WebSocket client disconnected: {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        log.debug("WebSocket transport error for {}: {}", session.getId(), exception.getMessage());
    }

    public void broadcast(TelemetryEvent event) {
        if (sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            log.error("Failed to serialize TelemetryEvent {}: {}", event.type(), ex.getMessage());
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            sendRaw(session, message);
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    private void sendToSession(WebSocketSession session, TelemetryEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            sendRaw(session, new TextMessage(json));
        } catch (Exception ex) {
            log.debug("Failed to send welcome message to {}: {}", session.getId(), ex.getMessage());
        }
    }

    private void sendRaw(WebSocketSession session, TextMessage message) {
        synchronized (session) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                } else {
                    sessions.remove(session);
                }
            } catch (IOException ex) {
                sessions.remove(session);
                log.debug("Removed broken WebSocket session {}: {}", session.getId(), ex.getMessage());
            }
        }
    }
}

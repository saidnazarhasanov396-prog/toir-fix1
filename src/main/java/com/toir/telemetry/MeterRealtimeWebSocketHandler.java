package com.toir.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.security.PermissionConstants;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

@Component
public class MeterRealtimeWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MeterRealtimeWebSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int SEND_BUFFER_SIZE_LIMIT = 64 * 1024;
    private static final Set<String> READ_AUTHORITIES = Set.of(
            PermissionConstants.METER_READ, "SYSTEM_ADMIN", PermissionConstants.WILDCARD);

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ConcurrentWebSocketSessionDecorator> sessions = new ConcurrentHashMap<>();

    public MeterRealtimeWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (!canReadMeters(session.getPrincipal())) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_SIZE_LIMIT));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        closeQuietly(session, CloseStatus.NOT_ACCEPTABLE);
    }

    @EventListener
    public void onUpdate(MeterRealtimeUpdate update) {
        String payload = serialize(update);
        if (payload == null) {
            return;
        }
        sessions.forEach((sessionId, session) -> send(sessionId, session, payload));
    }

    private String serialize(MeterRealtimeUpdate update) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "event",
                    "data", Map.of(
                            "event", "meter.reading.updated",
                            "meterId", update.meterId(),
                            "equipmentId", update.equipmentId(),
                            "value", update.value(),
                            "readAt", update.readAt(),
                            "source", update.source()
                    )
            ));
        } catch (JsonProcessingException exception) {
            log.warn("Unable to serialize realtime meter update", exception);
            return null;
        }
    }

    private void send(String sessionId, ConcurrentWebSocketSessionDecorator session, String payload) {
        if (!session.isOpen()) {
            sessions.remove(sessionId, session);
            return;
        }
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException | RuntimeException exception) {
            sessions.remove(sessionId, session);
            closeQuietly(session, CloseStatus.SERVER_ERROR);
            log.debug("Removed unavailable realtime meter WebSocket session {}", sessionId, exception);
        }
    }

    private boolean canReadMeters(Principal principal) {
        return principal instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(READ_AUTHORITIES::contains);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException exception) {
            log.debug("Unable to close realtime meter WebSocket session {}", session.getId(), exception);
        }
    }
}

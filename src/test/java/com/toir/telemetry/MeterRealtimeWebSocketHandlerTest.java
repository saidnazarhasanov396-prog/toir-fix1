package com.toir.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.toir.enums.MeterSource;
import com.toir.security.PermissionConstants;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeterRealtimeWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final MeterRealtimeWebSocketHandler handler = new MeterRealtimeWebSocketHandler(objectMapper);

    @Test
    void broadcastsUpdateOnlyToAuthorizedOpenSessions() throws Exception {
        WebSocketSession allowed = session("allowed", PermissionConstants.METER_READ, true);
        WebSocketSession denied = session("denied", PermissionConstants.METER_READING_CREATE, true);
        handler.afterConnectionEstablished(allowed);
        handler.afterConnectionEstablished(denied);

        handler.onUpdate(update());

        ArgumentCaptor<WebSocketMessage<?>> messages = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(allowed).sendMessage(messages.capture());
        JsonNode payload = objectMapper.readTree(((TextMessage) messages.getValue()).getPayload());
        assertThat(payload.path("type").asText()).isEqualTo("event");
        assertThat(payload.path("data").path("event").asText()).isEqualTo("meter.reading.updated");
        assertThat(payload.path("data").path("value").asDouble()).isEqualTo(321.5d);
        verify(denied, never()).sendMessage(any());
        verify(denied).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void removesClosedSessionBeforeBroadcasting() throws Exception {
        WebSocketSession closed = session("closed", PermissionConstants.METER_READ, false);
        handler.afterConnectionEstablished(closed);

        handler.onUpdate(update());

        verify(closed, never()).sendMessage(any());
    }

    @Test
    void clientMessagesAreRejectedWithoutCreatingReadings() throws Exception {
        WebSocketSession allowed = session("allowed", PermissionConstants.METER_READ, true);

        handler.handleMessage(allowed, new TextMessage("{\"value\":999999}"));

        verify(allowed).close(CloseStatus.NOT_ACCEPTABLE);
    }

    private WebSocketSession session(String id, String authority, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(authority))));
        when(session.isOpen()).thenReturn(open);
        return session;
    }

    private MeterRealtimeUpdate update() {
        return new MeterRealtimeUpdate(
                UUID.fromString("1705e4d9-47da-44bb-9158-4ee8d4c9976d"),
                UUID.fromString("179b4c3d-66ba-477e-8e0d-b8e3f3046a52"),
                321.5d,
                Instant.parse("2026-08-03T11:00:00Z"),
                MeterSource.IOT
        );
    }
}

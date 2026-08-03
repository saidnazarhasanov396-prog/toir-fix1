package com.toir.telemetry;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

public class SpringTelemetryWebSocketConnector implements TelemetryWebSocketConnector {

    private final StandardWebSocketClient client;

    public SpringTelemetryWebSocketConnector() {
        this(new StandardWebSocketClient());
    }

    SpringTelemetryWebSocketConnector(StandardWebSocketClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<TelemetryWebSocketConnection> connect(URI uri, Listener listener) {
        WebSocketHandler handler = new AbstractWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                listener.onText(message.getPayload());
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                listener.onError(exception);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
                listener.onClose();
            }
        };
        return client.execute(handler, uri.toString()).thenApply(SpringConnection::new);
    }

    private record SpringConnection(WebSocketSession session) implements TelemetryWebSocketConnection {
        @Override
        public void sendText(String payload) {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to send simulator WebSocket message", exception);
            }
        }

        @Override
        public void close() {
            try {
                session.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to close simulator WebSocket", exception);
            }
        }
    }
}

package com.toir.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-secret-key-at-least-32-bytes-long-please");
        props.setExpirationMinutes(60);
        props.setIssuer("toir-test");
        service = new JwtService(props);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatesAndParsesToken() {
        String token = service.generateToken(
                "user-1",
                "alice",
                List.of("SYSTEM_ADMIN"),
                Map.of("email", "alice@test.local", "permissions", List.of("read", "write"))
        );

        Claims claims = service.parse(token);
        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("username", String.class)).isEqualTo("alice");
        assertThat((List<String>) claims.get("authorities", List.class)).containsExactly("SYSTEM_ADMIN");
        assertThat((List<String>) claims.get("permissions", List.class)).containsExactly("read", "write");
        assertThat(claims.getIssuer()).isEqualTo("toir-test");
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.generateToken("user-1", "alice", List.of(), Map.of());
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThatThrownBy(() -> service.parse(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}

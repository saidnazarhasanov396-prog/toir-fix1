package com.toir.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String STRONG_SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void weakSecretFailsWithHumanReadableConfigurationMessage() {
        JwtProperties properties = jwtProperties("weak-demo-secret");

        assertThatThrownBy(() -> new JwtService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret must be at least 32 bytes for HS256")
                .hasMessageContaining("TOIR_JWT_SECRET")
                .hasMessageContaining("16 bytes");
    }

    @Test
    void strongSecretInitializesAndSignsToken() {
        JwtProperties properties = jwtProperties(STRONG_SECRET);
        JwtService jwtService = new JwtService(properties);

        String token = jwtService.generateToken(
                "user-1",
                "admin",
                List.of("ROLE_ADMIN"),
                Map.of("departmentId", "dept-1")
        );

        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("username")).isEqualTo("admin");
        assertThat(claims.get("departmentId")).isEqualTo("dept-1");
        assertThat(STRONG_SECRET.getBytes(StandardCharsets.UTF_8)).hasSizeGreaterThanOrEqualTo(32);
    }

    private static JwtProperties jwtProperties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpirationMinutes(720);
        properties.setIssuer("toir-backend");
        return properties;
    }
}

package com.toir.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceLifecycleTest {

    @Test
    void validTokenPreservesClaimsIssuerAndConfiguredLifetime() {
        JwtService service = service(secret("valid"), 10);
        String token = service.generateToken(
                "user-id",
                "user",
                List.of("USER_READ"),
                Map.of("permissions", List.of("USER_READ"), "departmentId", "department-id")
        );

        Claims claims = service.parse(token);

        assertThat(claims.getSubject()).isEqualTo("user-id");
        assertThat(claims.getIssuer()).isEqualTo("test-issuer");
        assertThat(claims.get("username", String.class)).isEqualTo("user");
        assertThat(claims.get("authorities", List.class)).containsExactly("USER_READ");
        assertThat(claims.get("permissions", List.class)).containsExactly("USER_READ");
        assertThat(claims.get("departmentId", String.class)).isEqualTo("department-id");
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = service(secret("expired"), -1);
        String token = service.generateToken("user-id", "user", List.of("USER_READ"), Map.of());

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService issuer = service(secret("issuer"), 10);
        JwtService verifier = service(secret("verifier"), 10);
        String token = issuer.generateToken("user-id", "user", List.of("USER_READ"), Map.of());

        assertThatThrownBy(() -> verifier.parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        JwtService service = service(secret("malformed"), 10);

        assertThatThrownBy(() -> service.parse("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    private JwtService service(String secret, long expirationMinutes) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setIssuer("test-issuer");
        properties.setExpirationMinutes(expirationMinutes);
        return new JwtService(properties);
    }

    private String secret(String discriminator) {
        return ("test-only-" + discriminator + "-signing-material-").repeat(2);
    }
}

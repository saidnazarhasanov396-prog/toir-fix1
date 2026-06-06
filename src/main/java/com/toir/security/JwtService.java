package com.toir.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.WeakKeyException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class JwtService {

    private static final int HS256_MIN_SECRET_BYTES = 32;
    private static final String JWT_SECRET_CONFIGURATION_MESSAGE =
            "JWT secret must be at least 32 bytes for HS256. Set TOIR_JWT_SECRET to a strong random value.";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        String secret = Objects.requireNonNull(properties.getSecret(),
                "JWT secret is not configured. Set TOIR_JWT_SECRET or app.security.jwt.secret for the active Spring profile.");
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < HS256_MIN_SECRET_BYTES) {
            throw new IllegalStateException(JWT_SECRET_CONFIGURATION_MESSAGE
                    + " Current value is " + secretBytes.length + " bytes.");
        }
        try {
            this.key = Keys.hmacShaKeyFor(secretBytes);
        } catch (WeakKeyException ex) {
            throw new IllegalStateException(JWT_SECRET_CONFIGURATION_MESSAGE, ex);
        }
    }

    public String generateToken(String userId, String username, List<String> authorities, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getExpirationMinutes() * 60);

        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(userId)
                .claim("username", username)
                .claim("authorities", authorities)
                .claims(extraClaims)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return properties.getExpirationMinutes() * 60;
    }
}

package com.toir.ai.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

public class ToirAiWebhookSignatureVerifier {

    private final ToirAiGatewayProperties properties;

    public ToirAiWebhookSignatureVerifier(ToirAiGatewayProperties properties) {
        this.properties = properties;
    }

    public boolean verify(byte[] rawBody, String... signatureHeaders) {
        if (!properties.hasWebhookSigningSecret()) {
            return false;
        }
        byte[] expectedMac = hmacSha256(properties.getWebhookSigningSecret(), rawBody == null ? new byte[0] : rawBody);
        String expectedHex = HexFormat.of().formatHex(expectedMac);
        String expectedBase64 = Base64.getEncoder().encodeToString(expectedMac);
        for (String header : signatureHeaders) {
            if (matches(header, expectedHex, expectedBase64)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String headerValue, String expectedHex, String expectedBase64) {
        if (headerValue == null || headerValue.isBlank()) {
            return false;
        }
        String value = headerValue.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("sha256=")) {
            value = value.substring("sha256=".length()).trim();
        }
        return constantTimeEqualsIgnoreCase(value, expectedHex)
                || constantTimeEquals(value, expectedBase64);
    }

    private static byte[] hmacSha256(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to compute webhook HMAC", exception);
        }
    }

    private static boolean constantTimeEqualsIgnoreCase(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        return MessageDigest.isEqual(
                a.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                b.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}

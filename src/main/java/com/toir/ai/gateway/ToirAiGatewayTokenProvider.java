package com.toir.ai.gateway;

import com.toir.ai.gateway.dto.ToirAiAccessTokenResponse;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;

public class ToirAiGatewayTokenProvider {

    static final String DEFAULT_TOKEN_PATH = "/auth/token";
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 3600L;

    private final ToirAiGatewayProperties properties;
    private final RestClient restClient;
    private final Object lock = new Object();
    private volatile String accessToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public ToirAiGatewayTokenProvider(ToirAiGatewayProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public String getAccessToken() {
        if (isTokenValid()) {
            return accessToken;
        }
        synchronized (lock) {
            if (isTokenValid()) {
                return accessToken;
            }
            refreshToken();
            return accessToken;
        }
    }

    public void invalidate() {
        synchronized (lock) {
            accessToken = null;
            expiresAt = Instant.EPOCH;
        }
    }

    private boolean isTokenValid() {
        return StringUtils.hasText(accessToken)
                && Instant.now().isBefore(expiresAt.minus(properties.getTokenRefreshSkew()));
    }

    private void refreshToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", properties.getAuthUsername());
        form.add("password", properties.getAuthPassword());

        ToirAiAccessTokenResponse response = restClient.post()
                .uri(properties.normalizedTokenPath())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(ToirAiAccessTokenResponse.class);

        if (response == null || !StringUtils.hasText(response.getAccessToken())) {
            throw new IllegalStateException("AI gateway failed to obtain access token");
        }

        accessToken = response.getAccessToken();
        long expiresIn = response.getExpiresIn() != null && response.getExpiresIn() > 0
                ? response.getExpiresIn()
                : DEFAULT_EXPIRES_IN_SECONDS;
        expiresAt = Instant.now().plusSeconds(expiresIn);
    }
}
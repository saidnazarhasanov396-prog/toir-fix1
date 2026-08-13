package com.toir.ai.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "toir.ai.gateway")
public class ToirAiGatewayProperties {

    private boolean enabled = false;
    private String baseUrl = "https://toir-ai.tenzorsoft.uz";
    private String authUsername = "";
    private String authPassword = "";
    private String tokenPath = ToirAiGatewayTokenProvider.DEFAULT_TOKEN_PATH;
    private Duration tokenRefreshSkew = Duration.ofSeconds(30);
    private String authenticationHeader = "";
    private String authenticationSecret = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(120);
    private long maxUploadBytes = 52_428_800L;
    private String webhookSigningSecret = "";
    private String webhookCallbackUrl = "https://api-toir.tenzorsoft.uz/api/ai-job/callback";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAuthUsername() { return authUsername; }
    public void setAuthUsername(String authUsername) { this.authUsername = authUsername; }
    public String getAuthPassword() { return authPassword; }
    public void setAuthPassword(String authPassword) { this.authPassword = authPassword; }
    public String getTokenPath() { return tokenPath; }
    public void setTokenPath(String tokenPath) { this.tokenPath = tokenPath; }
    public Duration getTokenRefreshSkew() { return tokenRefreshSkew; }
    public void setTokenRefreshSkew(Duration tokenRefreshSkew) { this.tokenRefreshSkew = tokenRefreshSkew; }
    public String getAuthenticationHeader() { return authenticationHeader; }
    public void setAuthenticationHeader(String authenticationHeader) { this.authenticationHeader = authenticationHeader; }
    public String getAuthenticationSecret() { return authenticationSecret; }
    public void setAuthenticationSecret(String authenticationSecret) { this.authenticationSecret = authenticationSecret; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public long getMaxUploadBytes() { return maxUploadBytes; }
    public void setMaxUploadBytes(long maxUploadBytes) { this.maxUploadBytes = maxUploadBytes; }
    public String getWebhookSigningSecret() { return webhookSigningSecret; }
    public void setWebhookSigningSecret(String webhookSigningSecret) { this.webhookSigningSecret = webhookSigningSecret; }
    public String getWebhookCallbackUrl() { return webhookCallbackUrl; }
    public void setWebhookCallbackUrl(String webhookCallbackUrl) { this.webhookCallbackUrl = webhookCallbackUrl; }

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("toir.ai.gateway.base-url is required when gateway is enabled");
        }
        return baseUrl.replaceAll("/+$", "");
    }

    public String normalizedTokenPath() {
        if (tokenPath == null || tokenPath.isBlank()) {
            return ToirAiGatewayTokenProvider.DEFAULT_TOKEN_PATH;
        }
        return tokenPath.startsWith("/") ? tokenPath : "/" + tokenPath;
    }

    public boolean hasJwtAuthentication() {
        return authUsername != null && !authUsername.isBlank()
                && authPassword != null && !authPassword.isBlank();
    }

    public boolean hasAuthentication() {
        return authenticationHeader != null && !authenticationHeader.isBlank()
                && authenticationSecret != null && !authenticationSecret.isBlank();
    }

    public boolean hasWebhookSigningSecret() {
        return webhookSigningSecret != null && !webhookSigningSecret.isBlank();
    }
}

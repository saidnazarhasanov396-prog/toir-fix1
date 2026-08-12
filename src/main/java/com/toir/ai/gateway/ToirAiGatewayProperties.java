package com.toir.ai.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "toir.ai.gateway")
public class ToirAiGatewayProperties {

    private boolean enabled = false;
    private String baseUrl = "https://toir-ai.tenzorsoft.uz";
    private String authenticationHeader = "";
    private String authenticationSecret = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(120);
    private long maxUploadBytes = 52_428_800L;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
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

    public String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("toir.ai.gateway.base-url is required when gateway is enabled");
        }
        return baseUrl.replaceAll("/+$", "");
    }

    public boolean hasAuthentication() {
        return authenticationHeader != null && !authenticationHeader.isBlank()
                && authenticationSecret != null && !authenticationSecret.isBlank();
    }
}

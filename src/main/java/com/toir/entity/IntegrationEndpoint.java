package com.toir.entity;
import com.toir.entity.IntegrationSyncStatus;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "integration_endpoints")
public class IntegrationEndpoint extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String system;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "auth_type")
    private String authType;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_sync_status")
    private IntegrationSyncStatus lastSyncStatus;

    @Column(name = "port")
    private Integer port;

    @Column(name = "base_path")
    private String basePath;

    @Column(name = "api_key")
    private String apiKey;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "sync_interval_minutes")
    private Integer syncIntervalMinutes;

    @Column(name = "sync_work_orders", nullable = false)
    @ColumnDefault("false")
    private boolean syncWorkOrders;

    @Column(name = "sync_downtimes", nullable = false)
    @ColumnDefault("false")
    private boolean syncDowntimes;

    @Column(name = "sync_defects", nullable = false)
    @ColumnDefault("false")
    private boolean syncDefects;

    @Column(name = "sync_scada", nullable = false)
    @ColumnDefault("false")
    private boolean syncScada;

    @Column(name = "sync_production", nullable = false)
    @ColumnDefault("false")
    private boolean syncProduction;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Integer getSyncIntervalMinutes() { return syncIntervalMinutes; }
    public void setSyncIntervalMinutes(Integer syncIntervalMinutes) { this.syncIntervalMinutes = syncIntervalMinutes; }
    public boolean isSyncWorkOrders() { return syncWorkOrders; }
    public void setSyncWorkOrders(boolean syncWorkOrders) { this.syncWorkOrders = syncWorkOrders; }
    public boolean isSyncDowntimes() { return syncDowntimes; }
    public void setSyncDowntimes(boolean syncDowntimes) { this.syncDowntimes = syncDowntimes; }
    public boolean isSyncDefects() { return syncDefects; }
    public void setSyncDefects(boolean syncDefects) { this.syncDefects = syncDefects; }
    public boolean isSyncScada() { return syncScada; }
    public void setSyncScada(boolean syncScada) { this.syncScada = syncScada; }
    public boolean isSyncProduction() { return syncProduction; }
    public void setSyncProduction(boolean syncProduction) { this.syncProduction = syncProduction; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public String getFullUrl() {
        String base = url != null ? url : "http://localhost";
        if (port != null) {
            base = base.replaceAll(":\\d+", "") + ":" + port;
        }
        if (basePath != null && !basePath.isBlank()) {
            base = base + (basePath.startsWith("/") ? basePath : "/" + basePath);
        }
        return base;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public IntegrationSyncStatus getLastSyncStatus() { return lastSyncStatus; }
    public void setLastSyncStatus(IntegrationSyncStatus lastSyncStatus) { this.lastSyncStatus = lastSyncStatus; }
}

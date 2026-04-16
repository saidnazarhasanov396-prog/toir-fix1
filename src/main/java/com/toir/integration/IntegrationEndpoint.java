package com.toir.integration;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

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

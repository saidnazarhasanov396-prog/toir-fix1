package com.toir.entity;
import com.toir.enums.IntegrationSyncStatus;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "integration_endpoints")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_secret")
    private String clientSecret;

    @Column(name = "company_inn")
    private String companyInn;

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
}

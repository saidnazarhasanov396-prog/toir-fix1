package com.toir.entity.equipmentlifecycleexport;

import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "equipment_lifecycle_export_jobs")
@Getter
@Setter
@NoArgsConstructor
public class EquipmentLifecycleExportJob {
    @Id
    private UUID id;
    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;
    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_snapshot", nullable = false, columnDefinition = "jsonb")
    private String requestSnapshot;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authorization_scope", nullable = false, columnDefinition = "jsonb")
    private String authorizationScope;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EquipmentLifecycleExportStatus status;
    @Column(name = "as_of", nullable = false)
    private Instant asOf;
    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion;
    @Column(name = "manifest_version", nullable = false, length = 16)
    private String manifestVersion;
    @Column(name = "context_profile", nullable = false, length = 80)
    private String contextProfile;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_policy", nullable = false, columnDefinition = "jsonb")
    private String resolvedPolicy;
    @Column(name = "policy_fingerprint", nullable = false, length = 64)
    private String policyFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(name = "selection_mode", nullable = false, length = 32)
    private EquipmentLifecycleExportScopeMode selectionMode;
    @Column(name = "selection_frozen", nullable = false)
    private boolean selectionFrozen;
    @Column(name = "selected_count", nullable = false)
    private long selectedCount;
    @Column(name = "completed_count", nullable = false)
    private long completedCount;
    @Column(name = "last_completed_ordinal", nullable = false)
    private long lastCompletedOrdinal = -1;
    @Column(name = "lease_owner", length = 160)
    private String leaseOwner;
    @Column(name = "lease_token")
    private UUID leaseToken;
    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;
    @Column(name = "failure_code", length = 80)
    private String failureCode;
    @Column(name = "failure_summary", length = 500)
    private String failureSummary;
    @Column(name = "failure_equipment_id")
    private UUID failureEquipmentId;
    @Column(name = "resume_allowed", nullable = false)
    private boolean resumeAllowed;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "finalization_time")
    private Instant finalizationTime;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "cleanup_claim_token")
    private UUID cleanupClaimToken;
    @Column(name = "cleanup_claim_until")
    private Instant cleanupClaimUntil;
    @Column(name = "staging_cleanup_complete", nullable = false)
    private boolean stagingCleanupComplete;
    @Column(name = "cleanup_complete", nullable = false)
    private boolean cleanupComplete;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;
}

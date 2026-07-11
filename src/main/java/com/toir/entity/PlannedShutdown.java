package com.toir.entity;

import com.toir.enums.PlanStatus;
import com.toir.enums.PlannedShutdownStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "planned_shutdowns")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlannedShutdown extends BaseEntity {

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(nullable = false)
    private String name;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "shutdown_type", nullable = false, length = 64)
    private String shutdownType = "PLANNED";

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "responsible_employee_id")
    private UUID responsibleEmployeeId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "planned_start_at", nullable = false)
    private Instant plannedStartAt;

    @Column(name = "planned_end_at", nullable = false)
    private Instant plannedEndAt;

    @Column(name = "approved_start_at")
    private Instant approvedStartAt;

    @Column(name = "approved_end_at")
    private Instant approvedEndAt;

    @Column(name = "effective_extension_end_at")
    private Instant effectiveExtensionEndAt;

    @Column(name = "actual_shutdown_at")
    private Instant actualShutdownAt;

    @Column(name = "actual_safe_state_at")
    private Instant actualSafeStateAt;

    @Column(name = "actual_repair_start_at")
    private Instant actualRepairStartAt;

    @Column(name = "actual_testing_start_at")
    private Instant actualTestingStartAt;

    @Column(name = "actual_startup_at")
    private Instant actualStartupAt;

    @Column(name = "actual_completed_at")
    private Instant actualCompletedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "objective", columnDefinition = "text")
    private String objective;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "risk_level", length = 32)
    private String riskLevel;

    @Column(name = "risk_score", precision = 9, scale = 4)
    private BigDecimal riskScore;

    @Column(name = "approval_scope_version")
    private Long approvalScopeVersion;

    @Column(name = "approval_scope_hash", length = 128)
    private String approvalScopeHash;

    @Column(name = "reschedule_reason", columnDefinition = "text")
    private String rescheduleReason;

    @Column(name = "extension_reason", columnDefinition = "text")
    private String extensionReason;

    @Column(name = "closure_version", nullable = false)
    private Long closureVersion = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlannedShutdownStatus status = PlannedShutdownStatus.DRAFT;

    /**
     * Temporary compatibility view for the legacy list DTO. Stage 2's typed detail contract replaces it.
     */
    @Deprecated(forRemoval = false)
    public PlanStatus getStatus() {
        return switch (status) {
            case DRAFT -> PlanStatus.DRAFT;
            case APPROVED -> PlanStatus.APPROVED;
            case SHUTDOWN_STARTED, SAFE_STATE, REPAIR_IN_PROGRESS, TESTING, STARTUP,
                    EMERGENCY_EXTENDED -> PlanStatus.IN_PROGRESS;
            case CLOSED, COMPLETED -> PlanStatus.CLOSED;
            case CANCELLED -> PlanStatus.CANCELLED;
            default -> PlanStatus.GENERATED;
        };
    }

    public PlannedShutdownStatus getLifecycleStatus() {
        return status;
    }

    public void setStatus(PlannedShutdownStatus status) {
        this.status = status;
    }

    /**
     * Temporary compatibility bridge for the legacy approval handler.
     */
    @Deprecated(forRemoval = false)
    public void setStatus(PlanStatus status) {
        this.status = switch (status) {
            case DRAFT -> PlannedShutdownStatus.DRAFT;
            case GENERATED -> PlannedShutdownStatus.SCOPE_FORMATION;
            case APPROVED -> PlannedShutdownStatus.APPROVED;
            case IN_PROGRESS -> PlannedShutdownStatus.REPAIR_IN_PROGRESS;
            case CLOSED -> PlannedShutdownStatus.CLOSED;
            case CANCELLED -> PlannedShutdownStatus.CANCELLED;
        };
    }

    @PrePersist
    void initializeCoreFields() {
        if (code == null || code.isBlank()) {
            code = "PS-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT);
        }
        if (plannedStartAt == null) {
            plannedStartAt = startAt;
        }
        if (plannedEndAt == null) {
            plannedEndAt = endAt;
        }
    }
}

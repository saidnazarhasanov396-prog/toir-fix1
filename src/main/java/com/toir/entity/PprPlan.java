package com.toir.entity;
import com.toir.enums.MaterializationMode;
import com.toir.enums.MaintenanceScheduleContentHashVersion;
import com.toir.enums.PlanStatus;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleRecurrenceAnchor;
import com.toir.enums.PprFrequency;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.PprType;
import com.toir.exception.MaintenanceScheduleCalculationConflictException;
import com.toir.exception.MaintenanceScheduleCalculationConflictException.Reason;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "ppr_plans")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PprPlan extends ActorStampedEntity {

    private static final Pattern CALCULATION_HASH_PATTERN =
            Pattern.compile("^[0-9a-f]{64}$");

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status = PlanStatus.DRAFT;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "ppr_type")
    private PprType pprType;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type")
    private PprScheduleType scheduleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency")
    private PprFrequency frequency;

    @Column(name = "interval_hours")
    private Long intervalHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type")
    private PprScopeType scopeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "anchor_mode")
    private MaintenanceScheduleAnchorMode anchorMode;

    @Builder.Default
    @Column(name = "shift_from_excluded_weekdays", nullable = false)
    private boolean shiftFromExcludedWeekdays = false;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "ppr_plan_excluded_weekdays",
            joinColumns = @JoinColumn(name = "plan_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "weekday", nullable = false)
    private Set<DayOfWeek> excludedWeekdays = new HashSet<>();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_recurrence_anchor", nullable = false)
    private MaintenanceScheduleRecurrenceAnchor recurrenceAnchor =
            MaintenanceScheduleRecurrenceAnchor.REGULATION_DATE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private PprPlanOrigin origin = PprPlanOrigin.MANUAL;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "materialization_mode", nullable = false)
    private MaterializationMode materializationMode = MaterializationMode.LEGACY_MATERIALIZED;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "task_materialization_status", nullable = false)
    private TaskMaterializationStatus taskMaterializationStatus =
            TaskMaterializationStatus.NOT_APPLICABLE;

    @Column(name = "calculation_revision")
    private Long calculationRevision;

    @Column(name = "calculation_content_hash", length = 64)
    private String calculationContentHash;

    @Column(name = "calculation_content_hash_version")
    private Integer calculationContentHashVersion;

    @Column(name = "materialized_revision")
    private Long materializedRevision;

    @Column(name = "materialized_task_count")
    private Integer materializedTaskCount;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<PprTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<PprPlanTarget> targets = new ArrayList<>();

    public void initializeApprovalFirstCalculation(
            long revision,
            int hashVersion,
            String hash) {
        requireApprovalFirstMode();
        validateCalculationBinding(revision, hashVersion, hash);
        if (calculationRevision != null) {
            if (calculationRevision == revision
                    && Objects.equals(calculationContentHashVersion, hashVersion)
                    && !hashEquals(calculationContentHash, hash)) {
                throw calculationConflict(Reason.HASH_MISMATCH);
            }
            throw calculationConflict(Reason.REVISION_CONFLICT);
        }
        if (revision != 1L) {
            throw calculationConflict(Reason.INVALID_REVISION_TRANSITION);
        }
        applyCalculationBinding(revision, hashVersion, hash);
    }

    public void advanceApprovalFirstCalculation(
            long expectedRevision,
            long nextRevision,
            int hashVersion,
            String hash) {
        requireApprovalFirstMode();
        validateCalculationBinding(nextRevision, hashVersion, hash);
        if (!Objects.equals(calculationRevision, expectedRevision)) {
            throw calculationConflict(Reason.REVISION_CONFLICT);
        }
        long requiredNext;
        try {
            requiredNext = Math.addExact(expectedRevision, 1L);
        } catch (ArithmeticException exception) {
            throw calculationConflict(Reason.INVALID_REVISION_TRANSITION);
        }
        if (expectedRevision < 1L || nextRevision != requiredNext) {
            throw calculationConflict(Reason.INVALID_REVISION_TRANSITION);
        }
        applyCalculationBinding(nextRevision, hashVersion, hash);
    }

    public boolean matchesCalculationBinding(
            long revision,
            int hashVersion,
            String hash) {
        if (materializationMode != MaterializationMode.APPROVAL_FIRST) {
            return false;
        }
        validateCalculationBinding(revision, hashVersion, hash);
        return Objects.equals(calculationRevision, revision)
                && Objects.equals(calculationContentHashVersion, hashVersion)
                && hashEquals(calculationContentHash, hash);
    }

    private void requireApprovalFirstMode() {
        if (materializationMode != MaterializationMode.APPROVAL_FIRST) {
            throw calculationConflict(Reason.INVALID_REVISION_TRANSITION);
        }
    }

    private static void validateCalculationBinding(
            long revision,
            int hashVersion,
            String hash) {
        if (revision < 1L) {
            throw calculationConflict(Reason.INVALID_REVISION_TRANSITION);
        }
        MaintenanceScheduleContentHashVersion.fromPersistedValue(hashVersion);
        if (hash == null || !CALCULATION_HASH_PATTERN.matcher(hash).matches()) {
            throw calculationConflict(Reason.INVALID_HASH);
        }
    }

    private void applyCalculationBinding(
            long revision,
            int hashVersion,
            String hash) {
        calculationRevision = revision;
        calculationContentHashVersion = hashVersion;
        calculationContentHash = hash;
    }

    private static boolean hashEquals(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.US_ASCII),
                second.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static MaintenanceScheduleCalculationConflictException
            calculationConflict(Reason reason) {
        return new MaintenanceScheduleCalculationConflictException(reason);
    }
}

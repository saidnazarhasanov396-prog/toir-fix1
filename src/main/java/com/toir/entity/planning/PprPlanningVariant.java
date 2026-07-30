package com.toir.entity.planning;

import com.toir.entity.BaseEntity;
import com.toir.enums.planning.PprPlanningVariantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "ppr_planning_variants",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ppr_planning_variant_session_name",
                columnNames = {"session_id", "normalized_name"})
)
@Getter
@Setter
public class PprPlanningVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PprPlanningSession session;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;

    @Column(nullable = false)
    private long revision;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "hash_version", nullable = false)
    private int hashVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PprPlanningVariantStatus status = PprPlanningVariantStatus.DRAFT;

    @Column(name = "inputs_json", columnDefinition = "text")
    private String inputsJson;

    @Column(name = "task_count")
    private Integer taskCount;

    @Column(name = "total_labor_hours", precision = 19, scale = 4)
    private BigDecimal totalLaborHours;

    @Column(name = "total_downtime_minutes")
    private Long totalDowntimeMinutes;

    @Column(name = "first_planned_date")
    private LocalDate firstPlannedDate;

    @Column(name = "last_planned_date")
    private LocalDate lastPlannedDate;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    @PreUpdate
    void normalizeName() {
        normalizedName = name == null ? null : name.trim().toLowerCase(Locale.ROOT);
    }
}

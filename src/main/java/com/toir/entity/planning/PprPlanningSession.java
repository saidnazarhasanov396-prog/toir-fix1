package com.toir.entity.planning;

import com.toir.entity.BaseEntity;
import com.toir.enums.planning.PprPlanningSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ppr_planning_sessions")
@Getter
@Setter
public class PprPlanningSession extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "planning_year", nullable = false)
    private int year;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PprPlanningSessionStatus status = PprPlanningSessionStatus.DRAFT;

    @Column(name = "selected_variant_id")
    private UUID selectedVariantId;

    @Column(name = "selected_variant_revision")
    private Long selectedVariantRevision;

    @Column(name = "selected_variant_hash", length = 64)
    private String selectedVariantHash;

    @Column(name = "selected_variant_hash_version")
    private Integer selectedVariantHashVersion;

    @Column(name = "approval_request_id")
    private UUID approvalRequestId;

    @Column(name = "approved_plan_id")
    private UUID approvedPlanId;

    @Version
    @Column(nullable = false)
    private long version;
}

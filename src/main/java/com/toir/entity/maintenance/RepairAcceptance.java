package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.enums.RepairAcceptanceQualityGrade;
import com.toir.enums.RepairAcceptanceStage;
import com.toir.enums.RepairAcceptanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "repair_acceptances")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RepairAcceptance extends BaseEntity {

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairAcceptanceStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepairAcceptanceStatus status = RepairAcceptanceStatus.DRAFT;

    @Column(name = "accepted_by_id")
    private UUID acceptedById;

    @Column(name = "handed_over_by_id")
    private UUID handedOverById;

    @Column(name = "acceptance_started_at")
    private Instant acceptanceStartedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "run_in_required", nullable = false)
    private boolean runInRequired;

    @Column(name = "run_in_shifts_required")
    private Integer runInShiftsRequired;

    @Column(name = "run_in_started_at")
    private Instant runInStartedAt;

    @Column(name = "run_in_completed_at")
    private Instant runInCompletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_grade")
    private RepairAcceptanceQualityGrade qualityGrade;

    @Column(name = "performance_before", columnDefinition = "text")
    private String performanceBefore;

    @Column(name = "performance_after", columnDefinition = "text")
    private String performanceAfter;

    @Column(name = "quality_before", columnDefinition = "text")
    private String qualityBefore;

    @Column(name = "quality_after", columnDefinition = "text")
    private String qualityAfter;

    @Column(columnDefinition = "text")
    private String remarks;

    @OneToMany(mappedBy = "acceptance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepairAcceptanceDefect> defects = new ArrayList<>();
}

package com.toir.entity.projects;
import com.toir.entity.BaseEntity;
import com.toir.entity.equipment.ProcurementRequestLine;
import com.toir.enums.PriorityLevel;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.enums.ProcurementRequestType;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Заявка на закупку ТМЦ/запчастей. По ТЗ §4.2.9 — формируется службой МТО
 * вручную или автоматически из low-stock, проходит согласование, после
 * одобрения переходит в заказ поставщику и затем в приход на склад.
 */
@Entity
@Table(name = "procurement_requests")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProcurementRequest extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "responsible_id")
    private UUID responsibleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriorityLevel priority = PriorityLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcurementRequestType type = ProcurementRequestType.SPARE_PART;

    @Column(name = "source_defect_id")
    private UUID sourceDefectId;

    @Column(name = "source_defect_title")
    private String sourceDefectTitle;

    @Column(name = "source_ppr_task_id")
    private UUID sourcePprTaskId;

    @Column(name = "source_ppr_task_title")
    private String sourcePprTaskTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcurementRequestStatus status = ProcurementRequestStatus.DRAFT;

    /** AUTO — сгенерирована автоматически из low-stock; MANUAL — создана пользователем. */
    @Column(nullable = false)
    private String source = "MANUAL";

    @Column(name = "required_by")
    private LocalDate requiredBy;

    @Column(name = "total_estimated_cost", nullable = false)
    private double totalEstimatedCost = 0.0;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProcurementRequestLine> lines = new ArrayList<>();
}

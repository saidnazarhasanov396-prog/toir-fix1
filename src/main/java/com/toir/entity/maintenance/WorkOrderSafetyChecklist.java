package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.enums.SafetyChecklistStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "work_order_safety_checklists")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WorkOrderSafetyChecklist extends BaseEntity {

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "template_id")
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SafetyChecklistStatus status = SafetyChecklistStatus.DRAFT;

    @Column(name = "checked_by_id")
    private UUID checkedById;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(columnDefinition = "text")
    private String remarks;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderSafetyChecklistItem> items = new ArrayList<>();
}

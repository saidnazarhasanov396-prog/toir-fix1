package com.toir.entity.maintenance;

import com.toir.entity.BaseEntity;
import com.toir.enums.SafetyChecklistCategory;
import com.toir.enums.SafetyChecklistItemStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_order_safety_checklist_items")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WorkOrderSafetyChecklistItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checklist_id", nullable = false)
    private WorkOrderSafetyChecklist checklist;

    @Column(name = "template_item_id")
    private UUID templateItemId;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SafetyChecklistCategory category = SafetyChecklistCategory.OTHER;

    @Column(nullable = false)
    private boolean critical;

    @Column(name = "requires_comment", nullable = false)
    private boolean requiresComment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SafetyChecklistItemStatus status = SafetyChecklistItemStatus.PENDING;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "checked_by_id")
    private UUID checkedById;

    @Column(name = "checked_at")
    private Instant checkedAt;
}

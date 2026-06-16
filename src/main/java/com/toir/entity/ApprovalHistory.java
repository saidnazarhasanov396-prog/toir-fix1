package com.toir.entity;

import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.ApprovalTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_history")
@Getter
@Setter
public class ApprovalHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_id", insertable = false, updatable = false)
    private ApprovalRequest approval;

    @Column(name = "approval_id", nullable = false)
    private UUID approvalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status")
    private ApprovalStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private ApprovalStatus newStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "delegated_for_id")
    private UUID delegatedForId;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type")
    private ApprovalActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private ApprovalTargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;
}

package com.toir.entity;

import com.toir.enums.ApprovalDecision;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_steps",
        uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "step_number"}))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApprovalStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest request;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "approver_id")
    private UUID approverId;

    @Column(name = "decided_by_id")
    private UUID decidedById;

    @Column(name = "delegated_for_id")
    private UUID delegatedForId;

    @Column(name = "approver_role")
    private String approverRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalDecision decision = ApprovalDecision.PENDING;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(columnDefinition = "text")
    private String comment;

    @Version
    @Column(name = "version")
    private Long version;
}

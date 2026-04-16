package com.toir.approval;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_steps",
        uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "step_number"}))
public class ApprovalStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest request;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "approver_role")
    private String approverRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalDecision decision = ApprovalDecision.PENDING;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(columnDefinition = "text")
    private String comment;

    public ApprovalRequest getRequest() { return request; }
    public void setRequest(ApprovalRequest request) { this.request = request; }
    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
    public UUID getApproverId() { return approverId; }
    public void setApproverId(UUID approverId) { this.approverId = approverId; }
    public String getApproverRole() { return approverRole; }
    public void setApproverRole(String approverRole) { this.approverRole = approverRole; }
    public ApprovalDecision getDecision() { return decision; }
    public void setDecision(ApprovalDecision decision) { this.decision = decision; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

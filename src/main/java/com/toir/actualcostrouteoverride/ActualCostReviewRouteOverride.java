package com.toir.actualcostrouteoverride;

import com.toir.common.jpa.BaseEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "actual_cost_review_route_overrides")
public class ActualCostReviewRouteOverride extends BaseEntity {

    @Column(name = "actual_cost_id", nullable = false)
    private UUID actualCostId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "approval_role_code", nullable = false)
    private String approvalRoleCode;

    @Column(name = "escalation_role_code")
    private String escalationRoleCode;

    @Column(name = "threshold_hours", nullable = false)
    private int thresholdHours = 24;

    @Column(nullable = false, columnDefinition = "text")
    private String comment;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by_id")
    private UUID createdById;

    @Column(name = "deactivated_by_id")
    private UUID deactivatedById;

    @Column(name = "deactivation_comment", columnDefinition = "text")
    private String deactivationComment;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    public UUID getActualCostId() { return actualCostId; }
    public void setActualCostId(UUID actualCostId) { this.actualCostId = actualCostId; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public String getApprovalRoleCode() { return approvalRoleCode; }
    public void setApprovalRoleCode(String approvalRoleCode) { this.approvalRoleCode = approvalRoleCode; }
    public String getEscalationRoleCode() { return escalationRoleCode; }
    public void setEscalationRoleCode(String escalationRoleCode) { this.escalationRoleCode = escalationRoleCode; }
    public int getThresholdHours() { return thresholdHours; }
    public void setThresholdHours(int thresholdHours) { this.thresholdHours = thresholdHours; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public UUID getCreatedById() { return createdById; }
    public void setCreatedById(UUID createdById) { this.createdById = createdById; }
    public UUID getDeactivatedById() { return deactivatedById; }
    public void setDeactivatedById(UUID deactivatedById) { this.deactivatedById = deactivatedById; }
    public String getDeactivationComment() { return deactivationComment; }
    public void setDeactivationComment(String deactivationComment) { this.deactivationComment = deactivationComment; }
    public Instant getDeactivatedAt() { return deactivatedAt; }
    public void setDeactivatedAt(Instant deactivatedAt) { this.deactivatedAt = deactivatedAt; }
}

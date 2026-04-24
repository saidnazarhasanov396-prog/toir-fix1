package com.toir.entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "actual_cost_review_route_overrides")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

}

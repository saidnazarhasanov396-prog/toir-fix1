package com.toir.entity.projects;

import com.toir.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "actual_cost_review_events")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ActualCostReviewEvent extends BaseEntity {

    @Column(name = "actual_cost_id", nullable = false)
    private UUID actualCostId;

    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "route_override_id")
    private UUID routeOverrideId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false)
    private String source;

    @Column(name = "event_group", nullable = false)
    private String eventGroup;

    @Column(name = "event_code", nullable = false)
    private String eventCode;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private String severity;

    private String status;

    @Column(name = "previous_approval_role_code")
    private String previousApprovalRoleCode;

    @Column(name = "next_approval_role_code")
    private String nextApprovalRoleCode;

    @Column(name = "previous_escalation_role_code")
    private String previousEscalationRoleCode;

    @Column(name = "next_escalation_role_code")
    private String nextEscalationRoleCode;

    @Column(name = "previous_threshold_hours")
    private Integer previousThresholdHours;

    @Column(name = "next_threshold_hours")
    private Integer nextThresholdHours;

    @Column(name = "handover_comment", columnDefinition = "text")
    private String handoverComment;

    @Column(name = "acknowledgement_comment", columnDefinition = "text")
    private String acknowledgementComment;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}

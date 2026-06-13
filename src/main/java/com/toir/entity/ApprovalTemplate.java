package com.toir.entity;

import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.NotificationSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "approval_templates")
@Getter
@Setter
public class ApprovalTemplate extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ApprovalTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_policy", nullable = false)
    private ApprovalRoutePolicy routePolicy = ApprovalRoutePolicy.ROLE_BASED;

    @Column(name = "approver_role")
    private String approverRole;

    @Column(name = "approver_id")
    private java.util.UUID approverId;

    @Column(name = "sla_hours", nullable = false)
    private int slaHours = 24;

    @Column(name = "escalation_hours", nullable = false)
    private int escalationHours = 24;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_severity", nullable = false)
    private NotificationSeverity escalationSeverity = NotificationSeverity.WARNING;

    @Column(nullable = false)
    private boolean active = true;
}

package com.toir.entity;

import com.toir.enums.ApprovalRoutePolicy;
import com.toir.enums.ApprovalFlowType;
import com.toir.enums.ApprovalRejectionPolicy;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.enums.ApprovalTieBreakPolicy;
import com.toir.enums.NotificationSeverity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "approval_templates")
@Getter
@Setter
public class ApprovalTemplate extends BaseEntity {

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false)
    private ApprovalFlowType flowType = ApprovalFlowType.SEQUENTIAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_policy", nullable = false)
    private ApprovalRejectionPolicy rejectionPolicy = ApprovalRejectionPolicy.TERMINATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "tie_break_policy")
    private ApprovalTieBreakPolicy tieBreakPolicy;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ApprovalTargetType targetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ApprovalActionType actionType = ApprovalActionType.APPROVE;

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

    @OneToMany(mappedBy = "template", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<ApprovalTemplateStep> steps = new ArrayList<>();
}

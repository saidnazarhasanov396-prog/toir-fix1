package com.toir.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "approval_template_steps",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_approval_template_steps_order",
                columnNames = {"template_id", "step_order"}
        )
)
@Getter
@Setter
public class ApprovalTemplateStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ApprovalTemplate template;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "approver_id")
    private UUID approverId;

    @Column(name = "approver_role")
    private String approverRole;
}

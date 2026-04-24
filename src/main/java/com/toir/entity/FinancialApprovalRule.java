package com.toir.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "financial_approval_rules")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FinancialApprovalRule extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "min_amount")
    private Double minAmount;

    @Column(name = "max_amount")
    private Double maxAmount;

    @Column(name = "required_role_code", nullable = false)
    private String requiredRoleCode;

    @Column(name = "escalate_to_role_code")
    private String escalateToRoleCode;

    @Column(name = "threshold_hours")
    private Integer thresholdHours;

    @Column(nullable = false)
    private int priority = 100;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

}

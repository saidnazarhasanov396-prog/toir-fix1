package com.toir.entity;

import com.toir.entity.BaseEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "financial_approval_rules")
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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getDepartmentId() { return departmentId; }
    public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
    public Double getMinAmount() { return minAmount; }
    public void setMinAmount(Double minAmount) { this.minAmount = minAmount; }
    public Double getMaxAmount() { return maxAmount; }
    public void setMaxAmount(Double maxAmount) { this.maxAmount = maxAmount; }
    public String getRequiredRoleCode() { return requiredRoleCode; }
    public void setRequiredRoleCode(String requiredRoleCode) { this.requiredRoleCode = requiredRoleCode; }
    public String getEscalateToRoleCode() { return escalateToRoleCode; }
    public void setEscalateToRoleCode(String escalateToRoleCode) { this.escalateToRoleCode = escalateToRoleCode; }
    public Integer getThresholdHours() { return thresholdHours; }
    public void setThresholdHours(Integer thresholdHours) { this.thresholdHours = thresholdHours; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

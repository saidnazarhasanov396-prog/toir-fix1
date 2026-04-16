package com.toir.financialapprovalrule.dto;

import com.toir.financialapprovalrule.FinancialApprovalRule;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record FinancialApprovalRuleDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name,
        UUID departmentId,
        Double minAmount,
        Double maxAmount,
        @NotBlank String requiredRoleCode,
        String escalateToRoleCode,
        Integer thresholdHours,
        Integer priority,
        String notes,
        Boolean isActive
) {
    public static FinancialApprovalRuleDto from(FinancialApprovalRule r) {
        return new FinancialApprovalRuleDto(r.getId(), r.getCode(), r.getName(), r.getDepartmentId(),
                r.getMinAmount(), r.getMaxAmount(), r.getRequiredRoleCode(), r.getEscalateToRoleCode(),
                r.getThresholdHours(), r.getPriority(), r.getNotes(), r.isActive());
    }
}

package com.toir.dto.actualcostrouteoverride;

import com.toir.enums.ActualCostStatus;

import java.util.UUID;

public record ActualCostRouteViewDto(
        UUID id,
        ActualCostStatus status,
        String routeSource,
        Boolean isOverdue,
        Integer hoursToOverdue,
        Integer ageHours,
        Double amount,
        String approvalRoleCode,
        String escalationRoleCode,
        String reviewComment,
        String notes,
        CostCategoryShortDto costCategory,
        ApprovalRuleShortDto approvalRule,
        Object reviewRouteOverride,
        DepartmentShortDto department,
        WorkOrderShortDto workOrder,
        CounteragentWorkShortDto counteragentWork
) {
}

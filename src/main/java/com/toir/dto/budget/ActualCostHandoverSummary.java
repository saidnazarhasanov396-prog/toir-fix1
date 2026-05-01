package com.toir.dto.budget;

import java.util.List;

public record ActualCostHandoverSummary(
        int total,
        int uniqueActualCosts,
        int uniqueDepartments,
        int uniqueTargetRoles,
        int uniqueActors,
        List<TargetRoleRow> byTargetRole,
        List<DepartmentRow> byDepartment
) {
    public record TargetRoleRow(String roleCode, long count, double amount) {
    }

    public record DepartmentRow(BudgetSummaryResponse.CategoryRef department, long count, double amount) {
    }
}

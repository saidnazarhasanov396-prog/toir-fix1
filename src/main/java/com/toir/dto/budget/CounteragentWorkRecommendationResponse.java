package com.toir.dto.budget;

import java.util.UUID;

public record CounteragentWorkRecommendationResponse(
        CounteragentWorkRef counteragentWork,
        double expectedAmount,
        double reflectedAmount,
        double submittedAmount,
        double pendingAmount,
        double remainingAmount,
        double remainingSubmissionAmount,
        String reflectionStatus,
        boolean canCreateActualCost,
        BudgetSummaryResponse.CategoryRef recommendedCostCategory,
        BudgetRef recommendedBudget,
        BudgetLineRef recommendedBudgetLine
) {
    public record CounteragentWorkRef(
            UUID id,
            String description,
            String status,
            Double cost,
            CounteragentRef counteragent,
            WorkOrderRef workOrder
    ) {
    }

    public record CounteragentRef(UUID id, String code, String name) {
    }

    public record WorkOrderRef(UUID id, String number, String title, UUID departmentId) {
    }

    public record BudgetRef(
            UUID id,
            int year,
            Integer month,
            String status,
            double totalPlanned,
            double totalActual,
            BudgetSummaryResponse.DepartmentRef department
    ) {
    }

    public record BudgetLineRef(
            UUID id,
            UUID budgetId,
            UUID costCategoryId,
            String description,
            double plannedAmount,
            double actualAmount
    ) {
    }
}

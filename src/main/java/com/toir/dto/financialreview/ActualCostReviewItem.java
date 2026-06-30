package com.toir.dto.financialreview;

import java.time.Instant;
import java.util.UUID;

public record ActualCostReviewItem(
        UUID id,
        UUID workOrderId,
        UUID repairRequestId,
        UUID counteragentWorkId,
        UUID costCategoryId,
        String status,
        double amount,
        Instant costDate,
        String notes,
        Instant reviewedAt,
        UUID reviewedById,
        String reviewedByName,
        UserRef reviewedBy,
        String reviewComment,
        Object counteragentWork,
        Object workOrder,
        Object repairRequest,
        Object budgetLine,
        Object department,
        Object costCategory,
        int ageHours,
        boolean isOverdue,
        String actionPath,
        Object approvalRule,
        String approvalRoleCode,
        String escalationRoleCode,
        int hoursToOverdue,
        String routeSource,
        Object reviewRouteOverride,
        boolean canReview,
        String reviewAccessReason,
        String effectiveReviewRoleCode,
        String contextType,
        String budgetActionPath,
        String reviewActionPath,
        String sourceLink,
        String allocationStatus,
        boolean unallocated,
        String allocationComment,
        Instant allocatedAt,
        UUID allocatedById
) {
    public ActualCostReviewItem(UUID id,
                                UUID workOrderId,
                                UUID repairRequestId,
                                UUID counteragentWorkId,
                                UUID costCategoryId,
                                String status,
                                double amount,
                                Instant costDate,
                                String notes,
                                Instant reviewedAt,
                                UUID reviewedById,
                                String reviewedByName,
                                UserRef reviewedBy,
                                String reviewComment,
                                Object counteragentWork,
                                Object workOrder,
                                Object repairRequest,
                                Object budgetLine,
                                Object department,
                                Object costCategory,
                                int ageHours,
                                boolean isOverdue,
                                String actionPath,
                                Object approvalRule,
                                String approvalRoleCode,
                                String escalationRoleCode,
                                int hoursToOverdue,
                                String routeSource,
                                Object reviewRouteOverride,
                                boolean canReview,
                                String reviewAccessReason,
                                String effectiveReviewRoleCode,
                                String contextType,
                                String budgetActionPath,
                                String reviewActionPath,
                                String sourceLink) {
        this(id, workOrderId, repairRequestId, counteragentWorkId, costCategoryId, status, amount, costDate, notes,
                reviewedAt, reviewedById, reviewedByName, reviewedBy, reviewComment, counteragentWork, workOrder,
                repairRequest, budgetLine, department, costCategory, ageHours, isOverdue, actionPath, approvalRule,
                approvalRoleCode, escalationRoleCode, hoursToOverdue, routeSource, reviewRouteOverride, canReview,
                reviewAccessReason, effectiveReviewRoleCode, contextType, budgetActionPath, reviewActionPath,
                sourceLink, budgetLine == null ? "UNALLOCATED" : "ALLOCATED", budgetLine == null,
                null, null, null);
    }

    public record Ref(UUID id, String code, String name) {
    }

    public record UserRef(UUID id, String fullName) {
    }

    public record WorkOrderRef(UUID id, String number, String title, Object department) {
    }

    public record CounteragentWorkRef(UUID id, String description, String status, Double cost, Object counteragent, Object workOrder) {
    }

    public record RepairRequestRef(UUID id, String number) {
    }

    public record BudgetLineRef(UUID id, String description, UUID budgetId, Object budget) {
    }

    public record ApprovalRuleRef(UUID id, String code, Integer thresholdHours) {
    }
}

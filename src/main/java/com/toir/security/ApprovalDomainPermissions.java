package com.toir.security;

import com.toir.enums.ApprovalTargetType;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Maps approval targets to domain permissions used when templates have no configured steps.
 * Approver steps store the permission code in {@code approverRole}; actors are matched via JWT authorities.
 */
public final class ApprovalDomainPermissions {

    private ApprovalDomainPermissions() {
    }

    public static Optional<String> approvePermissionFor(ApprovalTargetType targetType) {
        if (targetType == null) {
            return Optional.empty();
        }
        return switch (targetType) {
            case WORK_ORDER -> optional(PermissionConstants.WORK_ORDER_APPROVE);
            case PPR_PLAN -> optional(PermissionConstants.PPR_PLAN_APPROVE);
            case PPR_TASK -> optional(PermissionConstants.PPR_TASK_APPROVE);
            case REPAIR_REQUEST -> optional(PermissionConstants.REPAIR_REQUEST_APPROVE);
            case DEFECT_LIST -> optional(PermissionConstants.DEFECT_LIST_APPROVE);
            case PROCUREMENT_REQUEST, PROCUREMENT -> optional(PermissionConstants.PROCUREMENT_APPROVE);
            case ACTUAL_COST -> optional(PermissionConstants.ACTUAL_COST_APPROVE);
            case MAINTENANCE_BUDGET, BUDGET -> optional(PermissionConstants.BUDGET_APPROVE);
            case MAINTENANCE_DUE_EVENT -> optional(PermissionConstants.MAINTENANCE_EVENT_APPROVE);
            case MAINTENANCE_REGULATION, REGULATION_CHANGE_PROPOSAL ->
                    optional(PermissionConstants.MAINTENANCE_REGULATION_UPDATE);
            case EQUIPMENT_COMMISSIONING -> optional(PermissionConstants.EQUIPMENT_COMMISSIONING_UPDATE);
            case PLANNED_SHUTDOWN -> optional(PermissionConstants.WORK_ORDER_APPROVE);
            case REPAIR_CAMPAIGN -> optional(PermissionConstants.WORK_ORDER_APPROVE);
            case OTHER -> Optional.empty();
        };
    }

    private static Optional<String> optional(String permission) {
        return StringUtils.hasText(permission) ? Optional.of(permission) : Optional.empty();
    }
}

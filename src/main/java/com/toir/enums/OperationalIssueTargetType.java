package com.toir.enums;

/**
 * Semantic destinations exposed by operational issues. The frontend owns the
 * mapping from these domain values to application routes.
 */
public enum OperationalIssueTargetType {
    REPAIR_REQUEST,
    WORK_ORDER,
    PPR_TASK,
    PPR_PLAN,
    EQUIPMENT,
    DEFECT,
    MAINTENANCE_DUE_EVENT,
    SPARE_PART,
    WAREHOUSE,
    APPROVAL_REQUEST,
    MAINTENANCE_BUDGET
}

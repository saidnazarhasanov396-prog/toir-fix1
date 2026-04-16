package com.toir.repairrequest;

/**
 * Full status lifecycle per ТЗ §4.2.5.
 */
public enum RequestStatus {
    DRAFT,
    OPEN,
    REGISTERED,
    IN_REVIEW,
    NEEDS_CLARIFICATION,
    REJECTED,
    APPROVED,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CLOSED,
    CANCELLED
}

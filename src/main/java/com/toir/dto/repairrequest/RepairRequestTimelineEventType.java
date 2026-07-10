package com.toir.dto.repairrequest;

public enum RepairRequestTimelineEventType {
    CREATED,
    STATUS_CHANGE,
    ASSIGNED,
    CLARIFICATION_REQUESTED,
    WARRANTY_DECISION,
    DEFECT_LINKED,
    WORK_ORDER_LINKED,
    METER_READING,
    REJECTED,
    CLOSED
}

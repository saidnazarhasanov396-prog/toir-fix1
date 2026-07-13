package com.toir.enums;

import org.springframework.util.StringUtils;

import java.util.Locale;

public enum ApprovalTargetType {
    WORK_ORDER,
    PPR_PLAN,
    PPR_TASK,
    PROCUREMENT_REQUEST,
    PROCUREMENT,
    MAINTENANCE_BUDGET,
    BUDGET,
    REPAIR_REQUEST,
    MAINTENANCE_REGULATION,
    REGULATION_CHANGE_PROPOSAL,
    ACTUAL_COST,
    DEFECT_LIST,
    PLANNED_SHUTDOWN,
    REPAIR_CAMPAIGN,
    EQUIPMENT_COMMISSIONING,
    MAINTENANCE_DUE_EVENT,
    WAREHOUSE_WRITEOFF,
    OTHER;

    public static ApprovalTargetType fromDocumentType(String documentType) {
        if (!StringUtils.hasText(documentType)) {
            return null;
        }
        String normalized = documentType.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return ApprovalTargetType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}

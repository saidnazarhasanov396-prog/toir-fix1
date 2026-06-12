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
    ACTUAL_COST,
    MAINTENANCE_DUE_EVENT,
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

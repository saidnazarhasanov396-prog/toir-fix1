package com.toir.enums;

import com.toir.exception.RestException;

import java.util.Locale;

public enum AttachmentTargetType {
    EQUIPMENT,
    VEHICLE,
    WORK_ORDER,
    REPAIR_REQUEST,
    DEFECT,
    COMPLETION_ACT,
    APPROVAL,
    PROCUREMENT_REQUEST,
    STOCK_MOVEMENT,
    EQUIPMENT_COMMISSIONING;

    public static AttachmentTargetType from(String value) {
        if (value == null || value.isBlank()) {
            throw RestException.badRequest("targetType is required");
        }
        try {
            return AttachmentTargetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw RestException.badRequest("Invalid targetType: " + value);
        }
    }
}

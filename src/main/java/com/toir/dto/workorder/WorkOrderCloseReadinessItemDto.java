package com.toir.dto.workorder;

import com.toir.enums.CloseReadinessSeverity;

public record WorkOrderCloseReadinessItemDto(
        String code,
        String message,
        CloseReadinessSeverity severity,
        String group,
        String targetAction,
        String targetTab
) {
}

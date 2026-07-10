package com.toir.dto.repairrequest;

import com.toir.enums.CloseReadinessSeverity;

public record RepairRequestCloseReadinessItemDto(
        String code,
        String message,
        CloseReadinessSeverity severity,
        String group,
        String targetTab
) {
}

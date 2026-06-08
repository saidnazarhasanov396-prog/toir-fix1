package com.toir.dto.workorder;

import com.toir.enums.WorkOrderStatus;

public record WorkOrderStatusCountDto(
        WorkOrderStatus status,
        long count
) {
}

package com.toir.dto.workorder;

import java.util.List;

public record WorkOrderCalendarSummaryResponse(
        int year,
        Integer month,
        long totalOrders,
        List<WorkOrderStatusCountDto> statusCounts,
        List<WorkOrderCalendarBucketDto> months,
        List<WorkOrderCalendarBucketDto> days
) {
    public WorkOrderCalendarSummaryResponse {
        statusCounts = statusCounts == null ? List.of() : List.copyOf(statusCounts);
        months = months == null ? List.of() : List.copyOf(months);
        days = days == null ? List.of() : List.copyOf(days);
    }
}

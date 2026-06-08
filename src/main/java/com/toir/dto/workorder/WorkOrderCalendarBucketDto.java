package com.toir.dto.workorder;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

public record WorkOrderCalendarBucketDto(
        Integer month,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDate date,
        long totalOrders,
        List<WorkOrderStatusCountDto> statusCounts
) {
    public WorkOrderCalendarBucketDto {
        statusCounts = statusCounts == null ? List.of() : List.copyOf(statusCounts);
    }
}

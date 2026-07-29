package com.toir.dto.pprplanning.calendar;

import com.toir.enums.ApprovalStatus;
import com.toir.enums.PlanStatus;
import java.time.LocalDate;
import java.util.UUID;

public record PprEquipmentCalendarPlanSummary(
        UUID id,
        String code,
        String name,
        PlanStatus status,
        ApprovalStatus approvalStatus,
        LocalDate fromDate,
        LocalDate toDate
) {
}

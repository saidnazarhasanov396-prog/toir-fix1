package com.toir.dto.maintenanceschedule;

import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.enums.ApprovalStatus;
import com.toir.enums.MaintenanceScheduleRecurrenceAnchor;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

public record MaintenanceScheduleCalculationDto(
        PprPlanDto plan,
        ApprovalStatus approvalStatus,
        boolean shiftFromExcludedWeekdays,
        Set<DayOfWeek> excludedWeekdays,
        MaintenanceScheduleRecurrenceAnchor recurrenceAnchor,
        List<MaintenanceScheduleCalculationItemDto> calculationItems
) {
    public MaintenanceScheduleCalculationDto(
            PprPlanDto plan,
            ApprovalStatus approvalStatus,
            boolean shiftFromExcludedWeekdays,
            Set<DayOfWeek> excludedWeekdays,
            MaintenanceScheduleRecurrenceAnchor recurrenceAnchor
    ) {
        this(
                plan,
                approvalStatus,
                shiftFromExcludedWeekdays,
                excludedWeekdays,
                recurrenceAnchor,
                List.of()
        );
    }

    public MaintenanceScheduleCalculationDto(
            PprPlanDto plan,
            ApprovalStatus approvalStatus
    ) {
        this(
                plan,
                approvalStatus,
                false,
                Set.of(),
                MaintenanceScheduleRecurrenceAnchor.REGULATION_DATE,
                List.of()
        );
    }
}

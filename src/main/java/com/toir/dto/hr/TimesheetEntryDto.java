package com.toir.dto.hr;

import com.toir.entity.TimesheetEntry;
import com.toir.entity.TimesheetStatus;

import java.time.LocalDate;
import java.util.UUID;

public record TimesheetEntryDto(
        UUID id,
        UUID employeeId,
        LocalDate workDate,
        double hoursRegular,
        double hoursOvertime,
        double hoursNight,
        double hoursHoliday,
        UUID workOrderId,
        UUID costCategoryId,
        TimesheetStatus status,
        String note
) {
    public static TimesheetEntryDto from(TimesheetEntry e) {
        return new TimesheetEntryDto(
                e.getId(), e.getEmployeeId(), e.getWorkDate(),
                e.getHoursRegular(), e.getHoursOvertime(), e.getHoursNight(), e.getHoursHoliday(),
                e.getWorkOrderId(), e.getCostCategoryId(), e.getStatus(), e.getNote());
    }
}

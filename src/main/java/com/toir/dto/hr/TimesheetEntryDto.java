package com.toir.dto.hr;

import com.toir.entity.TimesheetEntry;
import com.toir.enums.TimesheetStatus;

import java.time.LocalDate;
import java.util.UUID;

public record TimesheetEntryDto(
        UUID id,
        UUID employeeId,
        String employeeName,
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
    public TimesheetEntryDto(
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
        this(
                id,
                employeeId,
                null,
                workDate,
                hoursRegular,
                hoursOvertime,
                hoursNight,
                hoursHoliday,
                workOrderId,
                costCategoryId,
                status,
                note
        );
    }

    public static TimesheetEntryDto from(TimesheetEntry e) {
        return from(e, null);
    }

    public static TimesheetEntryDto from(TimesheetEntry e, String employeeName) {
        return new TimesheetEntryDto(
                e.getId(), e.getEmployeeId(), employeeName, e.getWorkDate(),
                e.getHoursRegular(), e.getHoursOvertime(), e.getHoursNight(), e.getHoursHoliday(),
                e.getWorkOrderId(), e.getCostCategoryId(), e.getStatus(), e.getNote());
    }
}

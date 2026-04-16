package com.toir.hr.dto;

import com.toir.hr.TimesheetStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;
import java.util.UUID;

public record TimesheetEntryRequest(
        @NotNull UUID employeeId,
        @NotNull LocalDate workDate,
        @PositiveOrZero double hoursRegular,
        @PositiveOrZero double hoursOvertime,
        @PositiveOrZero double hoursNight,
        @PositiveOrZero double hoursHoliday,
        UUID workOrderId,
        UUID costCategoryId,
        TimesheetStatus status,
        String note
) {}

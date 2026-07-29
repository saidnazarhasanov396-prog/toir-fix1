package com.toir.service.maintanance;

import com.toir.exception.RestException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Canonical workday and timezone policy for generated PPR task windows.
 */
@Component
public class PprTaskScheduleWindowCalculator {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tashkent");
    private static final LocalTime WORKDAY_START = LocalTime.of(9, 0);
    private static final LocalTime WORKDAY_END = LocalTime.of(18, 0);
    private static final double NORMATIVE_HOURS_PER_WORKDAY = 8.0d;

    public ScheduleWindow calculate(
            LocalDate occurrenceDate,
            double normativeLaborHours,
            LocalDate planStart,
            LocalDate planEnd,
            Set<DayOfWeek> excludedWeekdays) {
        if (occurrenceDate == null || planStart == null || planEnd == null
                || planStart.isAfter(planEnd)) {
            throw conflict(
                    "PPR_TASK_SCHEDULE_RANGE_INVALID",
                    "PPR task schedule requires a valid occurrence and plan range");
        }
        if (!Double.isFinite(normativeLaborHours)
                || normativeLaborHours < 0.0d) {
            throw conflict(
                    "PPR_TASK_NORMATIVE_DURATION_INVALID",
                    "PPR task normative labor hours must be a finite non-negative value");
        }
        Set<DayOfWeek> excluded = excludedWeekdays == null
                ? Set.of()
                : Set.copyOf(excludedWeekdays);
        if (excluded.size() == DayOfWeek.values().length) {
            throw conflict(
                    "PPR_TASK_WORKDAY_POLICY_INVALID",
                    "PPR task workday policy cannot exclude every weekday");
        }
        if (occurrenceDate.isBefore(planStart) || occurrenceDate.isAfter(planEnd)) {
            throw outsidePlanRange();
        }

        long requiredWorkdays = Math.max(
                1L,
                (long) Math.ceil(normativeLaborHours / NORMATIVE_HOURS_PER_WORKDAY));
        LocalDate scheduledEndDate = occurrenceDate;
        for (long completed = 1L; completed < requiredWorkdays; completed++) {
            scheduledEndDate = nextIncludedDate(scheduledEndDate, excluded);
            if (scheduledEndDate.isAfter(planEnd)) {
                throw outsidePlanRange();
            }
        }

        LocalDateTime scheduledStart = occurrenceDate.atTime(WORKDAY_START);
        LocalDateTime scheduledEnd = scheduledEndDate.atTime(WORKDAY_END);
        if (!scheduledStart.isBefore(scheduledEnd)
                || scheduledEnd.toLocalDate().isAfter(planEnd)) {
            throw outsidePlanRange();
        }
        return new ScheduleWindow(scheduledStart, scheduledEnd, scheduledEnd);
    }

    public ZoneId businessZone() {
        return BUSINESS_ZONE;
    }

    private LocalDate nextIncludedDate(
            LocalDate current,
            Set<DayOfWeek> excludedWeekdays) {
        LocalDate candidate = current.plusDays(1);
        while (excludedWeekdays.contains(candidate.getDayOfWeek())) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private static RestException outsidePlanRange() {
        return conflict(
                "PPR_TASK_SCHEDULE_OUTSIDE_PLAN_RANGE",
                "PPR task normative duration does not fit the plan range");
    }

    private static RestException conflict(String code, String message) {
        return new RestException(message, HttpStatus.CONFLICT, code);
    }

    public record ScheduleWindow(
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            LocalDateTime dueDate) {
    }
}

package com.toir.service.maintanance;

import com.toir.exception.RestException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PprTaskScheduleWindowCalculatorTest {

    private final PprTaskScheduleWindowCalculator calculator =
            new PprTaskScheduleWindowCalculator();

    @Test
    void calculatesCanonicalWindowWithDueAtOrAfterScheduledEnd() {
        PprTaskScheduleWindowCalculator.ScheduleWindow window =
                calculator.calculate(
                        LocalDate.of(2026, 7, 27),
                        16.0d,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31),
                        Set.of());

        assertThat(window.scheduledStart())
                .isEqualTo(LocalDateTime.of(2026, 7, 27, 9, 0));
        assertThat(window.scheduledEnd())
                .isEqualTo(LocalDateTime.of(2026, 7, 28, 18, 0));
        assertThat(window.dueDate()).isEqualTo(window.scheduledEnd());
        assertThat(window.scheduledStart()).isBefore(window.scheduledEnd());
        assertThat(window.dueDate()).isAfterOrEqualTo(window.scheduledEnd());
        assertThat(calculator.businessZone()).isEqualTo(ZoneId.of("Asia/Tashkent"));
    }

    @Test
    void skipsExcludedWorkdaysWhilePreservingNormativeDuration() {
        PprTaskScheduleWindowCalculator.ScheduleWindow window =
                calculator.calculate(
                        LocalDate.of(2026, 7, 31),
                        16.0d,
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 8, 31),
                        Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));

        assertThat(window.scheduledEnd())
                .isEqualTo(LocalDateTime.of(2026, 8, 3, 18, 0));
    }

    @Test
    void rejectsOccurrenceWhoseNormativeDurationDoesNotFitPlanRange() {
        assertThatThrownBy(() -> calculator.calculate(
                LocalDate.of(2026, 12, 31),
                16.0d,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                Set.of()))
                .isInstanceOfSatisfying(RestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo("PPR_TASK_SCHEDULE_OUTSIDE_PLAN_RANGE"));
    }

    @Test
    void rejectsInvalidOrFullyExcludedWorkdayPolicy() {
        assertThatThrownBy(() -> calculator.calculate(
                LocalDate.of(2026, 7, 27),
                8.0d,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                Set.copyOf(java.util.List.of(DayOfWeek.values()))))
                .isInstanceOfSatisfying(RestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo("PPR_TASK_WORKDAY_POLICY_INVALID"));
    }
}

package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartNextRequiredActionCandidate;
import com.toir.enums.MeterType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SparePartNextRequiredActionServiceTest {

    @Test
    void primarySelectionUsesSeverityThenCalendarThenRatioWithoutSubtractingIncompatibleUnits() {
        SparePartNextRequiredActionCandidate earlyCalendar = candidate(
                "PARENT_DESIGN_LIFE", "UPCOMING", Instant.parse("2026-07-11T00:00:00Z"), null, null, null,
                null, "DESIGN_LIFE_REVIEW");
        SparePartNextRequiredActionCandidate lowMeterRatio = candidate(
                "MAINTENANCE", "WARNING", null, MeterType.ENGINE_HOURS,
                new BigDecimal("1000"), new BigDecimal("990"), new BigDecimal("0.01"), "MAINTENANCE_REQUIRED");
        SparePartNextRequiredActionCandidate blocker = candidate(
                "SPARE_PART_INSTALLATION", "OVERDUE", null, MeterType.CYCLES,
                new BigDecimal("500"), new BigDecimal("510"), BigDecimal.ZERO, "BLOCK_OPERATION");

        assertThat(SparePartNextRequiredActionService.choosePrimary(
                List.of(earlyCalendar, lowMeterRatio, blocker)))
                .containsSame(blocker);
    }

    @Test
    void sameSeverityUsesEarliestCalendarBeforeLowestMeterRatio() {
        SparePartNextRequiredActionCandidate calendar = candidate(
                "MAINTENANCE", "WARNING", Instant.parse("2026-07-12T00:00:00Z"), null,
                null, null, null, "WARNING_ONLY");
        SparePartNextRequiredActionCandidate meter = candidate(
                "SPARE_PART_INSTALLATION", "WARNING", null, MeterType.MILEAGE_KM,
                new BigDecimal("100"), new BigDecimal("99"), new BigDecimal("0.01"), "WARNING_ONLY");

        assertThat(SparePartNextRequiredActionService.choosePrimary(List.of(meter, calendar)))
                .containsSame(calendar);
    }

    private static SparePartNextRequiredActionCandidate candidate(
            String sourceType,
            String status,
            Instant dueAt,
            MeterType meterType,
            BigDecimal dueMeterValue,
            BigDecimal currentMeterValue,
            BigDecimal remainingRatio,
            String action) {
        return new SparePartNextRequiredActionCandidate(
                sourceType,
                UUID.randomUUID(),
                status,
                dueAt,
                meterType,
                dueMeterValue,
                currentMeterValue,
                dueMeterValue == null || currentMeterValue == null
                        ? null
                        : dueMeterValue.subtract(currentMeterValue),
                remainingRatio,
                action,
                "test"
        );
    }
}

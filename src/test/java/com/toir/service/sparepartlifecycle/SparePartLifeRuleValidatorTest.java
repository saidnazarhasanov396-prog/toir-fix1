package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartLifeLimitRequest;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleRequest;
import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SparePartLifeRuleValidatorTest {

    private final SparePartLifeRuleValidator validator = new SparePartLifeRuleValidator();

    @Test
    void acceptsCalendarAndMeterLimitsWithValidBounds() {
        SparePartLifeRuleRequest request = request(
                SparePartLifeCombinationMode.ANY,
                List.of(
                        new SparePartLifeLimitRequest(
                                SparePartLifeLimitKind.CALENDAR,
                                SparePartCalendarUnit.MONTH,
                                null,
                                null,
                                new BigDecimal("12"),
                                new BigDecimal("1"),
                                0
                        ),
                        new SparePartLifeLimitRequest(
                                SparePartLifeLimitKind.METER,
                                null,
                                MeterType.ENGINE_HOURS,
                                null,
                                new BigDecimal("1000.5"),
                                new BigDecimal("100.5"),
                                1
                        )
                )
        );

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void nonManualRuleRequiresAtLeastOneLimit() {
        assertThatThrownBy(() -> validator.validate(request(SparePartLifeCombinationMode.ALL, List.of())))
                .hasMessageStartingWith("RULE_LIMIT_REQUIRED:");
    }

    @Test
    void manualRuleMayHaveNoLimits() {
        assertThatCode(() -> validator.validate(request(SparePartLifeCombinationMode.MANUAL, List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidLimitShapesAndWarningBounds() {
        SparePartLifeLimitRequest calendarWithMeter = new SparePartLifeLimitRequest(
                SparePartLifeLimitKind.CALENDAR,
                SparePartCalendarUnit.DAY,
                MeterType.MILEAGE_KM,
                null,
                BigDecimal.TEN,
                BigDecimal.ONE,
                0
        );
        SparePartLifeLimitRequest warningOverLimit = new SparePartLifeLimitRequest(
                SparePartLifeLimitKind.METER,
                null,
                MeterType.MILEAGE_KM,
                null,
                BigDecimal.TEN,
                new BigDecimal("11"),
                0
        );

        assertThatThrownBy(() -> validator.validate(request(SparePartLifeCombinationMode.ANY, List.of(calendarWithMeter))))
                .hasMessageStartingWith("RULE_LIMIT_INVALID:");
        assertThatThrownBy(() -> validator.validate(request(SparePartLifeCombinationMode.ANY, List.of(warningOverLimit))))
                .hasMessageStartingWith("RULE_WARNING_INVALID:");
    }

    @Test
    void rejectsDuplicateSequencesAndInvalidEffectiveRange() {
        SparePartLifeLimitRequest first = meterLimit(0);
        SparePartLifeLimitRequest second = meterLimit(0);
        SparePartLifeRuleRequest duplicateSequence = request(
                SparePartLifeCombinationMode.ANY,
                List.of(first, second)
        );
        SparePartLifeRuleRequest invalidRange = new SparePartLifeRuleRequest(
                duplicateSequence.sparePartId(),
                duplicateSequence.equipmentId(),
                duplicateSequence.equipmentNodeId(),
                duplicateSequence.slotCode(),
                duplicateSequence.combinationMode(),
                duplicateSequence.dueAction(),
                duplicateSequence.active(),
                Instant.parse("2026-07-11T00:00:00Z"),
                Instant.parse("2026-07-10T00:00:00Z"),
                duplicateSequence.name(),
                duplicateSequence.description(),
                List.of(first)
        );

        assertThatThrownBy(() -> validator.validate(duplicateSequence))
                .hasMessageStartingWith("RULE_LIMIT_SEQUENCE_DUPLICATE:");
        assertThatThrownBy(() -> validator.validate(invalidRange))
                .hasMessageStartingWith("RULE_EFFECTIVE_RANGE_INVALID:");
    }

    private static SparePartLifeRuleRequest request(SparePartLifeCombinationMode mode,
                                                    List<SparePartLifeLimitRequest> limits) {
        return new SparePartLifeRuleRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                mode,
                SparePartDueAction.MAINTENANCE_REQUIRED,
                true,
                null,
                null,
                "Pump seal life",
                null,
                limits
        );
    }

    private static SparePartLifeLimitRequest meterLimit(int sequence) {
        return new SparePartLifeLimitRequest(
                SparePartLifeLimitKind.METER,
                null,
                MeterType.ENGINE_HOURS,
                null,
                BigDecimal.TEN,
                BigDecimal.ONE,
                sequence
        );
    }
}

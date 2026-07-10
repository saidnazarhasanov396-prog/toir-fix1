package com.toir.service.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.AppliedLifeLimitSnapshot;
import com.toir.dto.sparepartlifecycle.AppliedLifeRuleSnapshot;
import com.toir.dto.sparepartlifecycle.CurrentMeterValue;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluationInput;
import com.toir.enums.MeterType;
import com.toir.enums.sparepartlifecycle.SparePartCalendarUnit;
import com.toir.enums.sparepartlifecycle.SparePartDueAction;
import com.toir.enums.sparepartlifecycle.SparePartLifeCombinationMode;
import com.toir.enums.sparepartlifecycle.SparePartLifeLimitKind;
import com.toir.enums.sparepartlifecycle.SparePartLifecycleEvaluationState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SparePartLifecycleEvaluatorTest {

    private static final Instant INSTALLED_AT = Instant.parse("2024-02-29T10:00:00Z");
    private final SparePartLifecycleEvaluator evaluator = new SparePartLifecycleEvaluator();

    @Test
    void calendarYearsUseCalendarArithmeticAndExactLimitIsDue() {
        AppliedLifeLimitSnapshot limit = calendarLimit(SparePartCalendarUnit.YEAR, "1", "0");

        var before = evaluator.evaluate(input(
                SparePartLifeCombinationMode.ANY,
                List.of(limit),
                Map.of(),
                Map.of(),
                Instant.parse("2025-02-27T10:00:00Z"),
                false
        ));
        var exact = evaluator.evaluate(input(
                SparePartLifeCombinationMode.ANY,
                List.of(limit),
                Map.of(),
                Map.of(),
                Instant.parse("2025-02-28T10:00:00Z"),
                false
        ));

        assertThat(before.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.OK);
        assertThat(before.nextCalendarDueAt()).isEqualTo(Instant.parse("2025-02-28T10:00:00Z"));
        assertThat(exact.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.DUE);
    }

    @Test
    void meterWarningAndExactDueThresholdsAreInclusive() {
        UUID meterId = UUID.randomUUID();
        AppliedLifeLimitSnapshot limit = meterLimit(meterId, "100", "10");

        var warning = evaluator.evaluate(input(
                SparePartLifeCombinationMode.ANY,
                List.of(limit),
                Map.of(meterId, new BigDecimal("1000")),
                Map.of(meterId, new CurrentMeterValue(new BigDecimal("1090"), true, null)),
                INSTALLED_AT.plusSeconds(1),
                false
        ));
        var due = evaluator.evaluate(input(
                SparePartLifeCombinationMode.ANY,
                List.of(limit),
                Map.of(meterId, new BigDecimal("1000")),
                Map.of(meterId, new CurrentMeterValue(new BigDecimal("1100"), true, null)),
                INSTALLED_AT.plusSeconds(2),
                false
        ));

        assertThat(warning.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.WARNING);
        assertThat(warning.limits().getFirst().consumed()).isEqualByComparingTo("90");
        assertThat(due.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.DUE);
        assertThat(due.limits().getFirst().remaining()).isEqualByComparingTo("0");
    }

    @Test
    void validRolloverCalculatesConsumptionAndMissingRolloverIsError() {
        UUID meterId = UUID.randomUUID();
        AppliedLifeLimitSnapshot limit = meterLimit(meterId, "30", "5");
        Map<UUID, BigDecimal> baselines = Map.of(meterId, new BigDecimal("90"));

        var rollover = evaluator.evaluate(input(
                SparePartLifeCombinationMode.ANY,
                List.of(limit),
                baselines,
                Map.of(meterId, new CurrentMeterValue(new BigDecimal("10"), true, new BigDecimal("100"))),
                INSTALLED_AT.plusSeconds(1),
                false
        ));
        var negative = evaluator.evaluate(input(
                SparePartLifeCombinationMode.ANY,
                List.of(limit),
                baselines,
                Map.of(meterId, new CurrentMeterValue(new BigDecimal("10"), true, null)),
                INSTALLED_AT.plusSeconds(1),
                false
        ));

        assertThat(rollover.limits().getFirst().consumed()).isEqualByComparingTo("20");
        assertThat(rollover.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.OK);
        assertThat(negative.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.ERROR);
        assertThat(negative.errors()).contains("METER_NEGATIVE_DELTA");
    }

    @Test
    void missingOrInactiveMeterProducesEvaluationError() {
        UUID meterId = UUID.randomUUID();
        AppliedLifeLimitSnapshot limit = meterLimit(meterId, "30", "5");

        var missing = evaluator.evaluate(input(
                SparePartLifeCombinationMode.ANY,
                List.of(limit),
                Map.of(meterId, BigDecimal.ZERO),
                Map.of(),
                INSTALLED_AT,
                false
        ));
        var inactive = evaluator.evaluate(input(
                SparePartLifeCombinationMode.ANY,
                List.of(limit),
                Map.of(meterId, BigDecimal.ZERO),
                Map.of(meterId, new CurrentMeterValue(BigDecimal.ONE, false, null)),
                INSTALLED_AT,
                false
        ));

        assertThat(missing.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.ERROR);
        assertThat(missing.errors()).contains("METER_REQUIRED");
        assertThat(inactive.errors()).contains("METER_INACTIVE");
    }

    @Test
    void anyAndAllCombinationModesAggregateDeterministically() {
        UUID dueMeter = UUID.randomUUID();
        UUID freshMeter = UUID.randomUUID();
        List<AppliedLifeLimitSnapshot> limits = List.of(
                meterLimit(dueMeter, "10", "1"),
                meterLimit(freshMeter, "10", "1")
        );
        Map<UUID, BigDecimal> baselines = Map.of(dueMeter, BigDecimal.ZERO, freshMeter, BigDecimal.ZERO);
        Map<UUID, CurrentMeterValue> current = Map.of(
                dueMeter, new CurrentMeterValue(BigDecimal.TEN, true, null),
                freshMeter, new CurrentMeterValue(BigDecimal.ONE, true, null)
        );

        var any = evaluator.evaluate(input(
                SparePartLifeCombinationMode.ANY, limits, baselines, current, INSTALLED_AT, false));
        var all = evaluator.evaluate(input(
                SparePartLifeCombinationMode.ALL, limits, baselines, current, INSTALLED_AT, false));

        assertThat(any.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.DUE);
        assertThat(all.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.WARNING);
    }

    @Test
    void manualModeNeverAutoExpiresButAuthorizedManualDueCanMarkItDue() {
        UUID meterId = UUID.randomUUID();
        AppliedLifeLimitSnapshot limit = meterLimit(meterId, "10", "1");
        Map<UUID, BigDecimal> baselines = Map.of(meterId, BigDecimal.ZERO);
        Map<UUID, CurrentMeterValue> current = Map.of(
                meterId, new CurrentMeterValue(new BigDecimal("20"), true, null));

        var automatic = evaluator.evaluate(input(
                SparePartLifeCombinationMode.MANUAL, List.of(limit), baselines, current, INSTALLED_AT, false));
        var manual = evaluator.evaluate(input(
                SparePartLifeCombinationMode.MANUAL, List.of(limit), baselines, current, INSTALLED_AT, true));

        assertThat(automatic.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.WARNING);
        assertThat(manual.aggregateState()).isEqualTo(SparePartLifecycleEvaluationState.DUE);
    }

    private static SparePartLifecycleEvaluationInput input(
            SparePartLifeCombinationMode combinationMode,
            List<AppliedLifeLimitSnapshot> limits,
            Map<UUID, BigDecimal> baselines,
            Map<UUID, CurrentMeterValue> current,
            Instant evaluatedAt,
            boolean manualDue) {
        AppliedLifeRuleSnapshot rule = new AppliedLifeRuleSnapshot(
                UUID.randomUUID(),
                1,
                combinationMode,
                SparePartDueAction.MAINTENANCE_REQUIRED,
                limits
        );
        return new SparePartLifecycleEvaluationInput(
                UUID.randomUUID(),
                INSTALLED_AT,
                rule,
                baselines,
                current,
                evaluatedAt,
                manualDue
        );
    }

    private static AppliedLifeLimitSnapshot calendarLimit(SparePartCalendarUnit unit,
                                                          String limit,
                                                          String warning) {
        return new AppliedLifeLimitSnapshot(
                UUID.randomUUID(),
                SparePartLifeLimitKind.CALENDAR,
                unit,
                null,
                null,
                new BigDecimal(limit),
                new BigDecimal(warning),
                0
        );
    }

    private static AppliedLifeLimitSnapshot meterLimit(UUID meterId, String limit, String warning) {
        return new AppliedLifeLimitSnapshot(
                UUID.randomUUID(),
                SparePartLifeLimitKind.METER,
                null,
                MeterType.ENGINE_HOURS,
                meterId,
                new BigDecimal(limit),
                new BigDecimal(warning),
                0
        );
    }
}

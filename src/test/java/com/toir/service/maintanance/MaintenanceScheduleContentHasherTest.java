package com.toir.service.maintanance;

import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.DEPARTMENT;
import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.EQUIPMENT_A;
import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.EQUIPMENT_B;
import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.canonicalContent;
import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toir.enums.MaintenanceScheduleAnchorMode;
import com.toir.enums.MaintenanceScheduleContentHashVersion;
import com.toir.enums.MaintenanceScheduleRecurrenceAnchor;
import com.toir.enums.MaintenanceScheduleScopeType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PriorityLevel;
import com.toir.exception.MaintenanceScheduleCalculationConflictException;
import com.toir.exception.MaintenanceScheduleCalculationConflictException.Reason;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenanceScheduleContentHasherTest {

    private static final String GOLDEN_V1_HASH =
            "080002a094e8f411c64c5f888a218f363ab604507fb9ac96e2bc246b3dacd34c";

    private final MaintenanceScheduleContentHasher hasher =
            new MaintenanceScheduleContentHasher(
                    new MaintenanceScheduleCanonicalContentSerializerV1());

    @Test
    void computesTheFixedLowercaseSha256GoldenVectorThroughV1Router() {
        assertThat(hasher.compute(
                MaintenanceScheduleContentHashVersion.V1,
                canonicalContent()
        ))
                .isEqualTo(GOLDEN_V1_HASH)
                .isEqualTo(hasher.compute(1, canonicalContent()))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void inputAndSnapshotItemOrderDoNotChangeTheHash() {
        MaintenanceScheduleCalculationItemContent first =
                item("a".repeat(64), new BigDecimal("1.0"), PriorityLevel.HIGH);
        MaintenanceScheduleCalculationItemContent second =
                item("b".repeat(64), new BigDecimal("2.0"), PriorityLevel.LOW);
        MaintenanceScheduleCalculationContent forward = copy(
                canonicalContent(),
                List.of(EQUIPMENT_B, EQUIPMENT_A),
                Set.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
                List.of(second, first)
        );
        MaintenanceScheduleCalculationContent reversed = copy(
                canonicalContent(),
                List.of(EQUIPMENT_A, EQUIPMENT_B),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                List.of(first, second)
        );

        assertThat(hasher.compute(1, reversed))
                .isEqualTo(hasher.compute(1, forward));
    }

    @Test
    void planScopePeriodSelectionAnchorAndWeekdaysAffectTheHash() {
        MaintenanceScheduleCalculationContent baseline = canonicalContent();
        String baselineHash = hasher.compute(1, baseline);

        assertThat(hasher.compute(1, withPlanName(baseline, "Changed")))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withPeriod(
                baseline, baseline.periodStart().plusDays(1), baseline.periodEnd())))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withScope(
                baseline, MaintenanceScheduleScopeType.EQUIPMENT_TYPE,
                baseline.planScopeType(), baseline.departmentId())))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withScope(
                baseline, baseline.selectionScopeType(),
                PprScopeType.ENTERPRISE, baseline.departmentId())))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withScope(
                baseline, baseline.selectionScopeType(),
                baseline.planScopeType(), EQUIPMENT_B)))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, copy(
                baseline, List.of(EQUIPMENT_A), baseline.excludedWeekdays(),
                baseline.snapshotItems())))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withWeekdays(
                baseline, Set.of(DayOfWeek.MONDAY))))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withRecurrence(
                baseline, MaintenanceScheduleRecurrenceAnchor.REGULATION_DATE)))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withAnchorSettings(
                baseline, MaintenanceScheduleAnchorMode.CURRENT, true)))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withAnchorSettings(
                baseline, baseline.anchorMode(), false)))
                .isNotEqualTo(baselineHash);
    }

    @Test
    void sourceIdentityScheduleLaborAndPriorityAffectTheHash() {
        MaintenanceScheduleCalculationContent baseline = canonicalContent();
        String baselineHash = hasher.compute(1, baseline);

        assertThat(hasher.compute(1, withItem(
                baseline,
                item("b".repeat(64), new BigDecimal("1.00"), PriorityLevel.HIGH))))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withItem(
                baseline,
                withScheduledStart(
                        baseline.snapshotItems().getFirst(),
                        baseline.snapshotItems().getFirst().scheduledStart().plusHours(1)
                ))))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withItem(
                baseline,
                item("a".repeat(64), new BigDecimal("2.00"), PriorityLevel.HIGH))))
                .isNotEqualTo(baselineHash);
        assertThat(hasher.compute(1, withItem(
                baseline,
                item("a".repeat(64), new BigDecimal("1.00"), PriorityLevel.LOW))))
                .isNotEqualTo(baselineHash);
    }

    @Test
    void decimalScaleLocaleTimezoneAndUtf8DoNotChangeSemantics() {
        MaintenanceScheduleCalculationContent onePointZero = withPlanName(
                withItem(
                        canonicalContent(),
                        item("a".repeat(64), new BigDecimal("1.0"), PriorityLevel.HIGH)),
                "Техобслуживание"
        );
        MaintenanceScheduleCalculationContent onePointZeroZero = withPlanName(
                withItem(
                        canonicalContent(),
                        item("a".repeat(64), new BigDecimal("1.00"), PriorityLevel.HIGH)),
                "Техобслуживание"
        );
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        String baseline;
        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            baseline = hasher.compute(1, onePointZero);
            Locale.setDefault(Locale.FRANCE);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertThat(hasher.compute(1, onePointZeroZero)).isEqualTo(baseline);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void verifiesUsingStoredVersionAndConstantShapeValidation() {
        String hash = hasher.compute(1, canonicalContent());

        hasher.verify(1, canonicalContent(), hash);
        assertThat(hasher.fingerprint(hash)).isEqualTo(hash.substring(0, 12));

        assertConflict(
                () -> hasher.verify(1, canonicalContent(), null),
                Reason.INVALID_HASH,
                "PPR_CALCULATION_INVALID_HASH"
        );
        assertConflict(
                () -> hasher.verify(1, canonicalContent(), "ABC"),
                Reason.INVALID_HASH,
                "PPR_CALCULATION_INVALID_HASH"
        );
        assertConflict(
                () -> hasher.verify(1, withPlanName(canonicalContent(), "Changed"), hash),
                Reason.HASH_MISMATCH,
                "PPR_CALCULATION_HASH_MISMATCH"
        );
    }

    @Test
    void unsupportedHistoricalVersionNeverFallsBackToV1() {
        assertConflict(
                () -> hasher.compute(2, canonicalContent()),
                Reason.HASH_VERSION_UNSUPPORTED,
                "PPR_CALCULATION_HASH_VERSION_UNSUPPORTED"
        );
    }

    private static void assertConflict(
            Runnable operation,
            Reason expectedReason,
            String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(MaintenanceScheduleCalculationConflictException.class)
                .satisfies(error -> {
                    MaintenanceScheduleCalculationConflictException conflict =
                            (MaintenanceScheduleCalculationConflictException) error;
                    assertThat(conflict.getReason()).isEqualTo(expectedReason);
                    assertThat(conflict.getErrorCode()).isEqualTo(expectedCode);
                    assertThat(conflict.getMessage()).doesNotContain(
                            GOLDEN_V1_HASH,
                            "hashVersion=V1"
                    );
                });
    }

    private static MaintenanceScheduleCalculationContent withPlanName(
            MaintenanceScheduleCalculationContent source,
            String planName) {
        return new MaintenanceScheduleCalculationContent(
                planName, source.notes(), source.periodStart(), source.periodEnd(),
                source.selectionScopeType(), source.planScopeType(), source.departmentId(),
                source.equipmentIds(), source.equipmentTypeIds(), source.regulationIds(),
                source.anchorMode(), source.recurrenceAnchor(),
                source.shiftFromExcludedWeekdays(), source.excludedWeekdays(),
                source.calculationRevision(), source.snapshotItems()
        );
    }

    private static MaintenanceScheduleCalculationContent withPeriod(
            MaintenanceScheduleCalculationContent source,
            LocalDate start,
            LocalDate end) {
        return new MaintenanceScheduleCalculationContent(
                source.planName(), source.notes(), start, end,
                source.selectionScopeType(), source.planScopeType(), source.departmentId(),
                source.equipmentIds(), source.equipmentTypeIds(), source.regulationIds(),
                source.anchorMode(), source.recurrenceAnchor(),
                source.shiftFromExcludedWeekdays(), source.excludedWeekdays(),
                source.calculationRevision(), source.snapshotItems()
        );
    }

    private static MaintenanceScheduleCalculationContent withScope(
            MaintenanceScheduleCalculationContent source,
            MaintenanceScheduleScopeType selectionScope,
            PprScopeType planScope,
            UUID departmentId) {
        return new MaintenanceScheduleCalculationContent(
                source.planName(), source.notes(), source.periodStart(), source.periodEnd(),
                selectionScope, planScope, departmentId,
                source.equipmentIds(), source.equipmentTypeIds(), source.regulationIds(),
                source.anchorMode(), source.recurrenceAnchor(),
                source.shiftFromExcludedWeekdays(), source.excludedWeekdays(),
                source.calculationRevision(), source.snapshotItems()
        );
    }

    private static MaintenanceScheduleCalculationContent withWeekdays(
            MaintenanceScheduleCalculationContent source,
            Set<DayOfWeek> weekdays) {
        return copy(source, source.equipmentIds(), weekdays, source.snapshotItems());
    }

    private static MaintenanceScheduleCalculationContent withRecurrence(
            MaintenanceScheduleCalculationContent source,
            MaintenanceScheduleRecurrenceAnchor recurrenceAnchor) {
        return new MaintenanceScheduleCalculationContent(
                source.planName(), source.notes(), source.periodStart(), source.periodEnd(),
                source.selectionScopeType(), source.planScopeType(), source.departmentId(),
                source.equipmentIds(), source.equipmentTypeIds(), source.regulationIds(),
                source.anchorMode(), recurrenceAnchor,
                source.shiftFromExcludedWeekdays(), source.excludedWeekdays(),
                source.calculationRevision(), source.snapshotItems()
        );
    }

    private static MaintenanceScheduleCalculationContent withAnchorSettings(
            MaintenanceScheduleCalculationContent source,
            MaintenanceScheduleAnchorMode anchorMode,
            boolean shiftFromExcludedWeekdays) {
        return new MaintenanceScheduleCalculationContent(
                source.planName(), source.notes(), source.periodStart(), source.periodEnd(),
                source.selectionScopeType(), source.planScopeType(), source.departmentId(),
                source.equipmentIds(), source.equipmentTypeIds(), source.regulationIds(),
                anchorMode, source.recurrenceAnchor(), shiftFromExcludedWeekdays,
                source.excludedWeekdays(), source.calculationRevision(),
                source.snapshotItems()
        );
    }

    private static MaintenanceScheduleCalculationContent withItem(
            MaintenanceScheduleCalculationContent source,
            MaintenanceScheduleCalculationItemContent itemContent) {
        return copy(
                source, source.equipmentIds(), source.excludedWeekdays(),
                List.of(itemContent));
    }

    private static MaintenanceScheduleCalculationItemContent withScheduledStart(
            MaintenanceScheduleCalculationItemContent source,
            LocalDateTime scheduledStart) {
        return new MaintenanceScheduleCalculationItemContent(
                source.sourceItemKey(),
                source.sourceItemKeyVersion(),
                source.equipmentId(),
                source.regulationId(),
                source.maintenanceRuleId(),
                source.templateId(),
                source.maintenanceType(),
                source.triggerType(),
                source.triggerDiscriminator(),
                source.cycleOrdinal(),
                source.plannedDate(),
                scheduledStart,
                source.scheduledEnd(),
                source.dueDate(),
                source.normativeLaborHours(),
                source.priority(),
                source.departmentId(),
                source.taskTitleSnapshot()
        );
    }

    private static MaintenanceScheduleCalculationContent copy(
            MaintenanceScheduleCalculationContent source,
            List<UUID> equipmentIds,
            Set<DayOfWeek> weekdays,
            List<MaintenanceScheduleCalculationItemContent> items) {
        return new MaintenanceScheduleCalculationContent(
                source.planName(), source.notes(), source.periodStart(), source.periodEnd(),
                source.selectionScopeType(), source.planScopeType(), source.departmentId(),
                equipmentIds, source.equipmentTypeIds(), source.regulationIds(),
                source.anchorMode(), source.recurrenceAnchor(),
                source.shiftFromExcludedWeekdays(), weekdays,
                source.calculationRevision(), items
        );
    }
}

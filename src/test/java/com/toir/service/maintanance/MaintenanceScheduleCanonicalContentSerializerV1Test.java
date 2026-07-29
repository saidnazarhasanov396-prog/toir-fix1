package com.toir.service.maintanance;

import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.DEPARTMENT;
import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.EQUIPMENT_A;
import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.EQUIPMENT_B;
import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.EQUIPMENT_TYPE;
import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.REGULATION;
import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.RULE;
import static com.toir.service.maintanance.MaintenanceScheduleContentTestFixtures.canonicalContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toir.entity.maintenance.MaintenanceScheduleCalculationItem;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.PriorityLevel;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MaintenanceScheduleCanonicalContentSerializerV1Test {

    private final MaintenanceScheduleCanonicalContentSerializerV1 serializer =
            new MaintenanceScheduleCanonicalContentSerializerV1();

    @Test
    void serializesTheFixedGoldenVectorWithExplicitNormalizationAndOrdering() {
        String expected = """
                hashVersion=V1:1
                planName=V11:Annual Plan
                notes=N
                periodStart=V10:2026-01-01
                periodEnd=V10:2026-12-31
                selectionScopeType=V9:EQUIPMENT
                planScopeType=V10:DEPARTMENT
                departmentId=V36:dddddddd-dddd-dddd-dddd-dddddddddddd
                anchorMode=V19:RESET_TO_PLAN_START
                recurrenceAnchor=V12:SHIFTED_DATE
                shiftFromExcludedWeekdays=V4:true
                calculationRevision=V1:1
                equipmentIds.size=V1:2
                equipmentIds[0]=V36:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
                equipmentIds[1]=V36:bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb
                equipmentTypeIds.size=V1:1
                equipmentTypeIds[0]=V36:eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee
                regulationIds.size=V1:1
                regulationIds[0]=V36:cccccccc-cccc-cccc-cccc-cccccccccccc
                excludedWeekdays.size=V1:2
                excludedWeekdays[0]=V6:MONDAY
                excludedWeekdays[1]=V6:FRIDAY
                snapshotItems.size=V1:1
                snapshotItems[0].sourceItemKey=V64:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                snapshotItems[0].sourceItemKeyVersion=V1:1
                snapshotItems[0].equipmentId=V36:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
                snapshotItems[0].regulationId=V36:cccccccc-cccc-cccc-cccc-cccccccccccc
                snapshotItems[0].maintenanceRuleId=V36:11111111-1111-1111-1111-111111111111
                snapshotItems[0].templateId=N
                snapshotItems[0].maintenanceType=V10:PREVENTIVE
                snapshotItems[0].triggerType=V3:ANY
                snapshotItems[0].triggerDiscriminator=V8:calendar
                snapshotItems[0].cycleOrdinal=V1:2
                snapshotItems[0].plannedDate=V10:2026-02-03
                snapshotItems[0].scheduledStart=V26:2026-02-03T08:30:00.123456
                snapshotItems[0].scheduledEnd=N
                snapshotItems[0].dueDate=V19:2026-02-04T08:30:00
                snapshotItems[0].normativeLaborHours=V1:1
                snapshotItems[0].priority=V4:HIGH
                snapshotItems[0].departmentId=V36:dddddddd-dddd-dddd-dddd-dddddddddddd
                snapshotItems[0].taskTitleSnapshot=V12:Inspect pump
                """;

        assertThat(serializer.serialize(canonicalContent())).isEqualTo(expected);
    }

    @Test
    void collectionOrderDoesNotChangeCanonicalSerialization() {
        MaintenanceScheduleCalculationContent reordered =
                new MaintenanceScheduleCalculationContent(
                        "Annual Plan",
                        null,
                        canonicalContent().periodStart(),
                        canonicalContent().periodEnd(),
                        canonicalContent().selectionScopeType(),
                        canonicalContent().planScopeType(),
                        DEPARTMENT,
                        List.of(EQUIPMENT_A, EQUIPMENT_B),
                        List.of(EQUIPMENT_TYPE),
                        List.of(REGULATION),
                        canonicalContent().anchorMode(),
                        canonicalContent().recurrenceAnchor(),
                        true,
                        Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                        1L,
                        canonicalContent().snapshotItems()
                );

        assertThat(serializer.serialize(reordered))
                .isEqualTo(serializer.serialize(canonicalContent()));
    }

    @Test
    void canonicalContentRejectsDuplicateSnapshotSourceKeys() {
        MaintenanceScheduleCalculationItemContent first =
                canonicalContent().snapshotItems().getFirst();
        MaintenanceScheduleCalculationItemContent duplicate =
                MaintenanceScheduleContentTestFixtures.item(
                        first.sourceItemKey(),
                        new BigDecimal("2.0"),
                        PriorityLevel.LOW
                );

        assertThatThrownBy(() -> withSnapshotItems(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique non-null sourceItemKey");
    }

    @Test
    void nullEmptyAndDelimiterLikeStringsHaveDistinctFrames() {
        MaintenanceScheduleCalculationContent nullNotes = canonicalContent();
        MaintenanceScheduleCalculationContent emptyNotes = withNotes("");
        MaintenanceScheduleCalculationContent delimiterNotes = withNotes("a\nnotes=N");

        assertThat(serializer.serialize(nullNotes))
                .isNotEqualTo(serializer.serialize(emptyNotes))
                .isNotEqualTo(serializer.serialize(delimiterNotes));
        assertThat(serializer.serialize(emptyNotes)).contains("notes=V0:");
        assertThat(serializer.serialize(delimiterNotes)).contains("notes=V9:a\nnotes=N");
    }

    @Test
    void localizedHistoricalDisplayFieldsDoNotChangeTheGoldenContent() {
        MaintenanceScheduleCalculationItem english = snapshot("Pump", "Regulation");
        MaintenanceScheduleCalculationItem uzbek = snapshot("Nasos", "Reglament");
        MaintenanceScheduleCalculationItemContent englishContent =
                MaintenanceScheduleCalculationContentMapper.fromSnapshot(english);
        MaintenanceScheduleCalculationItemContent uzbekContent =
                MaintenanceScheduleCalculationContentMapper.fromSnapshot(uzbek);

        assertThat(englishContent)
                .isEqualTo(uzbekContent)
                .isEqualTo(canonicalContent().snapshotItems().getFirst());
        assertThat(serializer.serialize(withSnapshotItem(englishContent)))
                .isEqualTo(serializer.serialize(canonicalContent()));
    }

    @Test
    void semanticallyEquivalentDecimalsAndUnicodeNormalizeIdentically() {
        MaintenanceScheduleCalculationContent first = canonicalContent(
                " Cafe\u0301 ",
                canonicalContent().periodStart(),
                canonicalContent().periodEnd(),
                DEPARTMENT,
                canonicalContent().equipmentIds(),
                canonicalContent().excludedWeekdays(),
                canonicalContent().recurrenceAnchor(),
                MaintenanceScheduleContentTestFixtures.item(
                        "a".repeat(64), new BigDecimal("1.0"), null)
        );
        MaintenanceScheduleCalculationContent second = canonicalContent(
                "Café",
                canonicalContent().periodStart(),
                canonicalContent().periodEnd(),
                DEPARTMENT,
                canonicalContent().equipmentIds(),
                canonicalContent().excludedWeekdays(),
                canonicalContent().recurrenceAnchor(),
                MaintenanceScheduleContentTestFixtures.item(
                        "a".repeat(64), new BigDecimal("1.00"), null)
        );

        assertThat(serializer.serialize(first)).isEqualTo(serializer.serialize(second));
    }

    private static MaintenanceScheduleCalculationContent withNotes(String notes) {
        MaintenanceScheduleCalculationContent source = canonicalContent();
        return new MaintenanceScheduleCalculationContent(
                source.planName(),
                notes,
                source.periodStart(),
                source.periodEnd(),
                source.selectionScopeType(),
                source.planScopeType(),
                source.departmentId(),
                source.equipmentIds(),
                source.equipmentTypeIds(),
                source.regulationIds(),
                source.anchorMode(),
                source.recurrenceAnchor(),
                source.shiftFromExcludedWeekdays(),
                source.excludedWeekdays(),
                source.calculationRevision(),
                source.snapshotItems()
        );
    }

    private static MaintenanceScheduleCalculationContent withSnapshotItem(
            MaintenanceScheduleCalculationItemContent item) {
        return withSnapshotItems(List.of(item));
    }

    private static MaintenanceScheduleCalculationContent withSnapshotItems(
            List<MaintenanceScheduleCalculationItemContent> items) {
        MaintenanceScheduleCalculationContent source = canonicalContent();
        return new MaintenanceScheduleCalculationContent(
                source.planName(),
                source.notes(),
                source.periodStart(),
                source.periodEnd(),
                source.selectionScopeType(),
                source.planScopeType(),
                source.departmentId(),
                source.equipmentIds(),
                source.equipmentTypeIds(),
                source.regulationIds(),
                source.anchorMode(),
                source.recurrenceAnchor(),
                source.shiftFromExcludedWeekdays(),
                source.excludedWeekdays(),
                source.calculationRevision(),
                items
        );
    }

    private static MaintenanceScheduleCalculationItem snapshot(
            String equipmentName,
            String regulationName) {
        return MaintenanceScheduleCalculationItem.builder()
                .sourceItemKey("a".repeat(64))
                .sourceItemKeyVersion(1)
                .equipmentId(EQUIPMENT_A)
                .regulationId(REGULATION)
                .maintenanceRuleId(RULE)
                .maintenanceType(MaintenanceKind.PREVENTIVE)
                .triggerType(MaintenanceTriggerPolicy.ANY)
                .triggerDiscriminator(" calendar ")
                .cycleOrdinal(2L)
                .plannedDate(canonicalContent().snapshotItems().getFirst().plannedDate())
                .scheduledStart(
                        canonicalContent().snapshotItems().getFirst().scheduledStart())
                .dueDate(canonicalContent().snapshotItems().getFirst().dueDate())
                .normativeLaborHours(new BigDecimal("1.00"))
                .priority(PriorityLevel.HIGH)
                .departmentId(DEPARTMENT)
                .taskTitleSnapshot(" Inspect pump ")
                .equipmentCodeSnapshot("P-001")
                .equipmentNameSnapshot(equipmentName)
                .regulationNameSnapshot(regulationName)
                .maintenanceRuleNameSnapshot(regulationName)
                .templateNameSnapshot(regulationName)
                .build();
    }
}

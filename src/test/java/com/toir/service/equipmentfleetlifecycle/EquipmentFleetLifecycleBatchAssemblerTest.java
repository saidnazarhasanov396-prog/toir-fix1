package com.toir.service.equipmentfleetlifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.dto.equipmentfleetlifecycle.EquipmentFleetLifecycleV1;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.EquipmentRow;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.LatestReadingRow;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.MeterRow;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.RepairMeterSnapshotRow;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.RepairRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EquipmentFleetLifecycleBatchAssemblerTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-03T10:15:30Z");
    private static final UUID EQUIPMENT_1 = uuid("10000000-0000-0000-0000-000000000001");
    private static final UUID EQUIPMENT_2 = uuid("20000000-0000-0000-0000-000000000002");
    private static final UUID EQUIPMENT_3 = uuid("30000000-0000-0000-0000-000000000003");
    private static final UUID METER_1 = uuid("a0000000-0000-0000-0000-000000000001");
    private static final UUID METER_2 = uuid("a0000000-0000-0000-0000-000000000002");
    private static final UUID METER_3 = uuid("a0000000-0000-0000-0000-000000000003");
    private static final UUID READING_1 = uuid("b0000000-0000-0000-0000-000000000001");
    private static final UUID READING_2 = uuid("b0000000-0000-0000-0000-000000000002");
    private static final UUID WORK_ORDER_1 = uuid("c0000000-0000-0000-0000-000000000001");
    private static final UUID WORK_ORDER_2 = uuid("c0000000-0000-0000-0000-000000000002");
    private static final UUID WORK_ORDER_3 = uuid("c0000000-0000-0000-0000-000000000003");
    private static final UUID DEFECT_1 = uuid("d0000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_1 = uuid("e0000000-0000-0000-0000-000000000001");

    private final EquipmentFleetLifecycleBatchAssembler assembler =
            new EquipmentFleetLifecycleBatchAssembler();

    @Test
    void groupsMixedBatchRowsUnderTheCorrectEquipmentAndMapsEveryLineField() {
        Instant readAt = GENERATED_AT.minusSeconds(60);
        EquipmentRow first = equipment(EQUIPMENT_1, "EQ-001");
        EquipmentRow second = equipment(EQUIPMENT_2, "EQ-002");
        MeterRow firstMeter = meter(EQUIPMENT_1, METER_1, "Hours", "HOURS", 10.0, readAt);
        MeterRow secondMeter = meter(EQUIPMENT_2, METER_2, "Cycles", "CYCLES", 20.0, readAt);
        LatestReadingRow firstReading = reading(EQUIPMENT_1, METER_1, READING_1, 10.0, readAt);
        LatestReadingRow secondReading = reading(EQUIPMENT_2, METER_2, READING_2, 20.0, readAt);
        RepairRow firstRepair = repair(EQUIPMENT_1, WORK_ORDER_1, GENERATED_AT.minusSeconds(120),
                DEFECT_1, true, "Seal leak", null, null, null, false, null);
        RepairRow secondRepair = repair(EQUIPMENT_2, WORK_ORDER_2, GENERATED_AT.minusSeconds(90),
                null, false, null, null, null, REQUEST_1, true, "Bearing noise");

        List<EquipmentFleetLifecycleV1.Line> lines = assembler.assemble(
                List.of(second, first),
                List.of(secondMeter, firstMeter),
                List.of(secondReading, firstReading),
                List.of(secondRepair, firstRepair),
                List.of(snapshot(EQUIPMENT_2, WORK_ORDER_2, METER_2, READING_2, 20.0, readAt),
                        snapshot(EQUIPMENT_1, WORK_ORDER_1, METER_1, READING_1, 10.0, readAt)),
                GENERATED_AT);

        assertThat(lines).extracting(line -> line.equipment().id())
                .containsExactly(EQUIPMENT_1, EQUIPMENT_2);
        EquipmentFleetLifecycleV1.Line firstLine = lines.get(0);
        assertThat(firstLine.schemaVersion()).isEqualTo("1.0");
        assertThat(firstLine.generatedAt()).isEqualTo(GENERATED_AT);
        assertThat(firstLine.consistency()).isEqualTo("FIXED_AS_OF_READ_COMMITTED_BATCHES");
        assertThat(firstLine.equipment()).isEqualTo(new EquipmentFleetLifecycleV1.EquipmentCore(
                EQUIPMENT_1, "EQ-001", "Equipment EQ-001", "INV-EQ-001", "TECH-EQ-001",
                "SER-EQ-001", "Model EQ-001", uuid("41000000-0000-0000-0000-000000000001"),
                uuid("42000000-0000-0000-0000-000000000001"),
                uuid("43000000-0000-0000-0000-000000000001"),
                uuid("44000000-0000-0000-0000-000000000001"),
                uuid("45000000-0000-0000-0000-000000000001"), "ACTIVE", "HEAVY", "Acme",
                LocalDate.parse("2020-01-02"), LocalDate.parse("2019-12-03"),
                LocalDate.parse("2020-02-04"), 120, 10, 20_000L, "HOURS", METER_1, 20_000.0));
        assertThat(firstLine.meters()).extracting(EquipmentFleetLifecycleV1.Meter::meterId)
                .containsExactly(METER_1);
        assertThat(firstLine.meters().get(0).latestReading().readingId()).isEqualTo(READING_1);
        assertThat(firstLine.repairs()).extracting(EquipmentFleetLifecycleV1.Repair::workOrderId)
                .containsExactly(WORK_ORDER_1);
        assertThat(lines.get(1).meters()).extracting(EquipmentFleetLifecycleV1.Meter::meterId)
                .containsExactly(METER_2);
        assertThat(lines.get(1).repairs()).extracting(EquipmentFleetLifecycleV1.Repair::workOrderId)
                .containsExactly(WORK_ORDER_2);
    }

    @Test
    void ordersMetersAndRepairsDeterministicallyAndUsesTheFinalRepairAsLastRepair() {
        Instant sameCompletion = GENERATED_AT.minusSeconds(100);
        UUID lowerWorkOrder = uuid("00000000-0000-0000-0000-000000000010");
        UUID higherWorkOrder = uuid("00000000-0000-0000-0000-000000000020");
        UUID earlierWorkOrder = uuid("ffffffff-0000-0000-0000-000000000030");
        MeterRow hoursZulu = meter(EQUIPMENT_1, METER_3, "Zulu", "HOURS", null, null);
        MeterRow cycles = meter(EQUIPMENT_1, METER_2, "Cycles", "CYCLES", null, null);
        MeterRow hoursAlpha = meter(EQUIPMENT_1, METER_1, "Alpha", "HOURS", null, null);

        EquipmentFleetLifecycleV1.Line line = assembler.assemble(
                List.of(equipment(EQUIPMENT_1, "EQ-001")),
                List.of(hoursZulu, cycles, hoursAlpha), List.of(),
                List.of(
                        repair(EQUIPMENT_1, higherWorkOrder, sameCompletion, DEFECT_1, true,
                                "reason", null, null, null, false, null),
                        repair(EQUIPMENT_1, earlierWorkOrder, sameCompletion.minusSeconds(1), DEFECT_1, true,
                                "reason", null, null, null, false, null),
                        repair(EQUIPMENT_1, lowerWorkOrder, sameCompletion, DEFECT_1, true,
                                "reason", null, null, null, false, null)),
                List.of(), GENERATED_AT).get(0);

        assertThat(line.meters()).extracting(EquipmentFleetLifecycleV1.Meter::meterId)
                .containsExactly(METER_2, METER_1, METER_3);
        assertThat(line.repairs()).extracting(EquipmentFleetLifecycleV1.Repair::workOrderId)
                .containsExactly(earlierWorkOrder, lowerWorkOrder, higherWorkOrder);
        assertThat(line.lastRepair()).isSameAs(line.repairs().get(2));
        assertThat(line.lastRepair().workOrderId()).isEqualTo(higherWorkOrder);
    }

    @Test
    void prefersAValidDefectReasonThenFallsBackToAValidRepairRequestThenNull() {
        RepairRow defectWins = repair(EQUIPMENT_1, WORK_ORDER_1, GENERATED_AT.minusSeconds(300),
                DEFECT_1, true, "Defect description", "Seal", "Wear", REQUEST_1, true,
                "Request description");
        RepairRow requestFallback = repair(EQUIPMENT_1, WORK_ORDER_2, GENERATED_AT.minusSeconds(200),
                DEFECT_1, false, "must not leak", null, null, REQUEST_1, true,
                " Request\n description ");
        RepairRow noReason = repair(EQUIPMENT_1, WORK_ORDER_3, GENERATED_AT.minusSeconds(100),
                null, false, null, null, null, null, false, null);

        List<EquipmentFleetLifecycleV1.Repair> repairs = assembler.assemble(
                List.of(equipment(EQUIPMENT_1, "EQ-001")), List.of(), List.of(),
                List.of(noReason, requestFallback, defectWins), List.of(), GENERATED_AT).get(0).repairs();

        assertThat(repairs.get(0).reason()).isEqualTo(new EquipmentFleetLifecycleV1.RepairReason(
                "DEFECT", "Defect description", "Seal", "Wear"));
        assertThat(repairs.get(1).reason()).isEqualTo(new EquipmentFleetLifecycleV1.RepairReason(
                "REPAIR_REQUEST", "Request  description", null, null));
        assertThat(repairs.get(2).reason()).isNull();
    }

    @Test
    void ignoresInvalidCrossEquipmentLinkTextAndEmitsBrokenLinkIssues() {
        RepairRow crossLinked = repair(EQUIPMENT_1, WORK_ORDER_1, GENERATED_AT.minusSeconds(100),
                DEFECT_1, false, "foreign defect", "foreign failure", "foreign root",
                REQUEST_1, false, "foreign request");

        EquipmentFleetLifecycleV1.Line line = assembler.assemble(
                List.of(equipment(EQUIPMENT_1, "EQ-001")), List.of(), List.of(),
                List.of(crossLinked), List.of(), GENERATED_AT).get(0);

        assertThat(line.repairs().get(0).reason()).isNull();
        assertThat(line.dataQuality().issues())
                .extracting(EquipmentFleetLifecycleV1.QualityIssue::code)
                .contains(EquipmentFleetLifecycleV1.QualityIssueCode.BROKEN_DEFECT_LINK,
                        EquipmentFleetLifecycleV1.QualityIssueCode.BROKEN_REPAIR_REQUEST_LINK);
        assertThat(line.dataQuality().issues())
                .filteredOn(issue -> issue.code() == EquipmentFleetLifecycleV1.QualityIssueCode.BROKEN_DEFECT_LINK)
                .singleElement().satisfies(issue -> assertThat(issue.relatedIds())
                        .containsExactly(WORK_ORDER_1, DEFECT_1));
    }

    @Test
    void includesOneRepairSnapshotPerMeterAndKeepsMissingReadingsExplicit() {
        Instant repairTime = GENERATED_AT.minusSeconds(100);
        RepairRow repair = repair(EQUIPMENT_1, WORK_ORDER_1, repairTime,
                DEFECT_1, true, "reason", null, null, null, false, null);

        EquipmentFleetLifecycleV1.Repair assembled = assembler.assemble(
                List.of(equipment(EQUIPMENT_1, "EQ-001")),
                List.of(meter(EQUIPMENT_1, METER_2, "Cycles", "CYCLES", null, null),
                        meter(EQUIPMENT_1, METER_1, "Hours", "HOURS", null, null)),
                List.of(), List.of(repair),
                List.of(snapshot(EQUIPMENT_1, WORK_ORDER_1, METER_1, READING_1, 44.0,
                        repairTime.minusSeconds(1))), GENERATED_AT).get(0).repairs().get(0);

        assertThat(assembled.meterReadingsAtRepair()).hasSize(2);
        assertThat(assembled.meterReadingsAtRepair().get(0))
                .isEqualTo(new EquipmentFleetLifecycleV1.RepairMeterSnapshot(
                        METER_2, "CYCLES", "unit", "MISSING", null, null, null));
        assertThat(assembled.meterReadingsAtRepair().get(1))
                .isEqualTo(new EquipmentFleetLifecycleV1.RepairMeterSnapshot(
                        METER_1, "HOURS", "unit", "AVAILABLE", READING_1, 44.0,
                        repairTime.minusSeconds(1)));
    }

    @Test
    void producesAllStableIssueCodesAndCalculatesCompletenessFromIssueEmptiness() {
        Instant readAt = GENERATED_AT.minusSeconds(50);
        MeterRow mismatchedCache = meter(EQUIPMENT_2, METER_1, "Hours", "HOURS", 999.0, readAt);
        MeterRow missingReading = meter(EQUIPMENT_2, METER_2, "Cycles", "CYCLES", null, null);
        LatestReadingRow latest = reading(EQUIPMENT_2, METER_1, READING_1, 10.0, readAt);
        RepairRow brokenRepair = repair(EQUIPMENT_2, WORK_ORDER_1, GENERATED_AT.minusSeconds(100),
                DEFECT_1, false, "foreign", null, null, REQUEST_1, false, "foreign");
        MeterRow cleanMeter = meter(EQUIPMENT_3, METER_3, "Hours", "HOURS", 30.0, readAt);
        LatestReadingRow cleanReading = reading(EQUIPMENT_3, METER_3, READING_2, 30.0, readAt);
        RepairRow cleanRepair = repair(EQUIPMENT_3, WORK_ORDER_2, GENERATED_AT.minusSeconds(90),
                DEFECT_1, true, "valid", null, null, null, false, null);

        List<EquipmentFleetLifecycleV1.Line> lines = assembler.assemble(
                List.of(equipment(EQUIPMENT_1, "EQ-001"), equipment(EQUIPMENT_2, "EQ-002"),
                        equipment(EQUIPMENT_3, "EQ-003")),
                List.of(mismatchedCache, missingReading, cleanMeter),
                List.of(latest, cleanReading), List.of(brokenRepair, cleanRepair),
                List.of(snapshot(EQUIPMENT_3, WORK_ORDER_2, METER_3, READING_2, 30.0, readAt)),
                GENERATED_AT);

        Set<EquipmentFleetLifecycleV1.QualityIssueCode> codes = lines.stream()
                .flatMap(line -> line.dataQuality().issues().stream())
                .map(EquipmentFleetLifecycleV1.QualityIssue::code)
                .collect(Collectors.toSet());
        assertThat(codes).containsExactlyInAnyOrder(EquipmentFleetLifecycleV1.QualityIssueCode.values());
        assertThat(lines.get(0).lastRepair()).isNull();
        assertThat(lines.get(0).dataQuality().complete()).isFalse();
        assertThat(lines.get(1).dataQuality().complete()).isFalse();
        assertThat(lines.get(2).dataQuality().issues()).isEmpty();
        assertThat(lines.get(2).dataQuality().complete()).isTrue();
        assertThat(lines.get(1).dataQuality().issues()).allSatisfy(issue -> {
            assertThat(issue.message()).isNotBlank();
            assertThat(issue.relatedIds()).doesNotContainNull();
        });
    }

    @Test
    void normalizesBlankAndControlWhitespaceAndTruncatesReasonTextTo512Characters() {
        String longDescription = "  Pump\tfailed\n" + "x".repeat(600) + "  ";
        RepairRow repair = repair(EQUIPMENT_1, WORK_ORDER_1, GENERATED_AT.minusSeconds(100),
                DEFECT_1, true, longDescription, " \t\n ", " Root\r cause ", null, false, null);

        EquipmentFleetLifecycleV1.RepairReason reason = assembler.assemble(
                List.of(equipment(EQUIPMENT_1, "EQ-001")), List.of(), List.of(),
                List.of(repair), List.of(), GENERATED_AT).get(0).repairs().get(0).reason();

        assertThat(reason.description()).hasSize(512).startsWith("Pump failed ")
                .doesNotContain("\t", "\n", "\r");
        assertThat(reason.failureReason()).isNull();
        assertThat(reason.rootCause()).isEqualTo("Root  cause");
    }

    @Test
    void detectsCachedMeterValueAndTimeMismatches() {
        Instant readAt = GENERATED_AT.minusSeconds(50);
        MeterRow valueMismatch = meter(EQUIPMENT_1, METER_1, "Hours", "HOURS", 11.0, readAt);
        MeterRow timeMismatch = meter(EQUIPMENT_1, METER_2, "Cycles", "CYCLES", 20.0,
                readAt.minusSeconds(1));

        EquipmentFleetLifecycleV1.Line line = assembler.assemble(
                List.of(equipment(EQUIPMENT_1, "EQ-001")), List.of(valueMismatch, timeMismatch),
                List.of(reading(EQUIPMENT_1, METER_1, READING_1, 10.0, readAt),
                        reading(EQUIPMENT_1, METER_2, READING_2, 20.0, readAt)),
                List.of(), List.of(), GENERATED_AT).get(0);

        assertThat(line.dataQuality().issues())
                .filteredOn(issue -> issue.code() == EquipmentFleetLifecycleV1.QualityIssueCode.METER_CACHE_MISMATCH)
                .extracting(EquipmentFleetLifecycleV1.QualityIssue::relatedIds)
                .containsExactly(List.of(METER_2), List.of(METER_1));
    }

    private static EquipmentRow equipment(UUID id, String code) {
        return new EquipmentRow(id, code, "Equipment " + code, "INV-" + code, "TECH-" + code,
                "SER-" + code, "Model " + code,
                uuid("41000000-0000-0000-0000-000000000001"),
                uuid("42000000-0000-0000-0000-000000000001"),
                uuid("43000000-0000-0000-0000-000000000001"),
                uuid("44000000-0000-0000-0000-000000000001"),
                uuid("45000000-0000-0000-0000-000000000001"),
                "ACTIVE", "HEAVY", "Acme", LocalDate.parse("2020-01-02"),
                LocalDate.parse("2019-12-03"), LocalDate.parse("2020-02-04"), 120, 10, 20_000L,
                "HOURS", METER_1, 20_000.0);
    }

    private static MeterRow meter(
            UUID equipmentId, UUID meterId, String name, String type,
            Double cachedValue, Instant cachedAt) {
        return new MeterRow(equipmentId, meterId, name, type, "unit", true, false,
                99_999.0, cachedValue, cachedAt);
    }

    private static LatestReadingRow reading(
            UUID equipmentId, UUID meterId, UUID readingId, Double value, Instant readAt) {
        return new LatestReadingRow(equipmentId, meterId, readingId, value, 1.0, readAt,
                "MANUAL", "WORK_COMPLETED", REQUEST_1, WORK_ORDER_1, DEFECT_1);
    }

    private static RepairRow repair(
            UUID equipmentId, UUID workOrderId, Instant completedAt,
            UUID defectId, boolean defectValid, String defectDescription, String failureReason,
            String rootCause, UUID requestId, boolean requestValid, String requestDescription) {
        return new RepairRow(equipmentId, workOrderId, "WO-" + workOrderId, "Repair title",
                "CORRECTIVE", "REPAIR", "COMPLETED", "HIGH", null, defectId, requestId,
                null, null, completedAt.minusSeconds(3_600), completedAt.minusSeconds(1_800),
                completedAt.minusSeconds(3_000), completedAt, 50L, "summary", "result", "closed",
                defectValid, defectDescription, failureReason, rootCause,
                requestValid, requestDescription);
    }

    private static RepairMeterSnapshotRow snapshot(
            UUID equipmentId, UUID workOrderId, UUID meterId, UUID readingId,
            Double value, Instant readAt) {
        return new RepairMeterSnapshotRow(
                equipmentId, workOrderId, meterId, "ignored-row-type", "ignored-row-unit",
                readingId, value, readAt);
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}

package com.toir.dto.equipmentfleetlifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentFleetLifecycleV1Test {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-03T10:15:30Z");
    private static final UUID EQUIPMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LAST_WORK_ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesTheV1LineContract() throws Exception {
        EquipmentFleetLifecycleV1.Repair lastRepair = repair(LAST_WORK_ORDER_ID, "WO-002");
        EquipmentFleetLifecycleV1.Line line = new EquipmentFleetLifecycleV1.Line(
                EquipmentFleetLifecycleV1.SCHEMA_VERSION,
                GENERATED_AT,
                EquipmentFleetLifecycleV1.CONSISTENCY,
                equipment(),
                List.of(meter()),
                lastRepair,
                List.of(repair(UUID.fromString("33333333-3333-3333-3333-333333333333"), "WO-001"), lastRepair),
                new EquipmentFleetLifecycleV1.DataQuality(false, List.of(new EquipmentFleetLifecycleV1.QualityIssue(
                        EquipmentFleetLifecycleV1.QualityIssueCode.REPAIR_WITHOUT_REASON,
                        "Repair has no linked failure reason",
                        List.of(LAST_WORK_ORDER_ID)))));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(line));

        assertThat(json.at("/schemaVersion").asText()).isEqualTo("1.0");
        assertThat(json.at("/generatedAt").asText()).isEqualTo(GENERATED_AT.toString());
        assertThat(json.at("/consistency").asText())
                .isEqualTo("FIXED_AS_OF_READ_COMMITTED_BATCHES");
        assertThat(json.at("/equipment/id").asText()).isEqualTo(EQUIPMENT_ID.toString());
        assertThat(json.at("/meters/0/latestReading/value").decimalValue())
                .isEqualByComparingTo("12864.5");
        assertThat(json.at("/repairs").size()).isEqualTo(2);
        assertThat(json.at("/lastRepair/workOrderId").asText()).isEqualTo(LAST_WORK_ORDER_ID.toString());
        assertThat(json.at("/dataQuality/issues").isArray()).isTrue();
    }

    @Test
    void serializesEmptyListsNullLastRepairAndNoRestrictedFields() throws Exception {
        EquipmentFleetLifecycleV1.Line line = new EquipmentFleetLifecycleV1.Line(
                EquipmentFleetLifecycleV1.SCHEMA_VERSION,
                GENERATED_AT,
                EquipmentFleetLifecycleV1.CONSISTENCY,
                equipment(), null, null, null, null);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(line));

        assertThat(json.at("/meters").isArray()).isTrue();
        assertThat(json.at("/meters").size()).isZero();
        assertThat(json.at("/repairs").isArray()).isTrue();
        assertThat(json.at("/repairs").size()).isZero();
        assertThat(json.at("/lastRepair").isNull()).isTrue();
        assertThat(json.toString()).doesNotContain("reporterId", "recordedByUserId", "deviceId", "note");
    }

    @Test
    void rejectsNullGeneratedAt() {
        assertThatThrownBy(() -> line(null, EquipmentFleetLifecycleV1.SCHEMA_VERSION,
                EquipmentFleetLifecycleV1.CONSISTENCY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("generatedAt is required");
    }

    @Test
    void rejectsInvalidMetadataConstants() {
        assertThatThrownBy(() -> line(GENERATED_AT, "2.0", EquipmentFleetLifecycleV1.CONSISTENCY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("schemaVersion must be 1.0");
        assertThatThrownBy(() -> line(GENERATED_AT, EquipmentFleetLifecycleV1.SCHEMA_VERSION, "READ_COMMITTED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("consistency must be FIXED_AS_OF_READ_COMMITTED_BATCHES");
    }

    private EquipmentFleetLifecycleV1.Line line(Instant generatedAt, String schemaVersion, String consistency) {
        return new EquipmentFleetLifecycleV1.Line(
                schemaVersion, generatedAt, consistency, equipment(), null, null, null, null);
    }

    private EquipmentFleetLifecycleV1.EquipmentCore equipment() {
        return new EquipmentFleetLifecycleV1.EquipmentCore(
                EQUIPMENT_ID, "EQ-001", "Excavator", "INV-001", "TECH-001", "SERIAL-001", "Model X",
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                UUID.fromString("88888888-8888-8888-8888-888888888888"),
                "ACTIVE", "HEAVY", "Acme", LocalDate.parse("2020-01-01"), LocalDate.parse("2019-12-01"),
                LocalDate.parse("2020-02-01"), 120, 10, 20_000L, "HOURS",
                UUID.fromString("99999999-9999-9999-9999-999999999999"), 20_000.0);
    }

    private EquipmentFleetLifecycleV1.Meter meter() {
        return new EquipmentFleetLifecycleV1.Meter(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "Engine hours", "HOURS", "h", true, true,
                99_999.0, 12_864.5, GENERATED_AT, new EquipmentFleetLifecycleV1.Reading(
                        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), 12_864.5, 12.5, GENERATED_AT,
                        "MANUAL", "WORK_COMPLETED", null, LAST_WORK_ORDER_ID, null));
    }

    private EquipmentFleetLifecycleV1.Repair repair(UUID workOrderId, String number) {
        return new EquipmentFleetLifecycleV1.Repair(
                workOrderId, number, "Hydraulic repair", "CORRECTIVE", "REPAIR", "COMPLETED", "HIGH", null,
                null, null, null, null, GENERATED_AT.minusSeconds(7_200), GENERATED_AT.minusSeconds(3_600),
                GENERATED_AT.minusSeconds(7_000), GENERATED_AT.minusSeconds(1_800), 5_200L, "Leak fixed", "Passed",
                "Closed", new EquipmentFleetLifecycleV1.RepairReason("DEFECT", "Hydraulic leak", "Seal failure", "Wear"),
                List.of(new EquipmentFleetLifecycleV1.RepairMeterSnapshot(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "HOURS", "h", "AVAILABLE",
                        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), 12_864.5, GENERATED_AT.minusSeconds(1_800))));
    }
}

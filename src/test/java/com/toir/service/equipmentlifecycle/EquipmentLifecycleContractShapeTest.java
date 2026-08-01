package com.toir.service.equipmentlifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class EquipmentLifecycleContractShapeTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void nullNumericValueIsSerializedAsNullAndMoneyRetainsCurrency() {
        var measurement = new EquipmentLifecycleContextV1.MeasurementValue(null, "h");
        var money = new EquipmentLifecycleContextV1.MoneyAmount(
                new BigDecimal("1250.5000"), "UZS");

        assertThat(mapper.valueToTree(measurement).get("value").isNull()).isTrue();
        assertThat(mapper.valueToTree(money).path("currency").asText()).isEqualTo("UZS");
        assertThat(mapper.valueToTree(money).path("amount").decimalValue())
                .isEqualByComparingTo("1250.5000");
    }

    @Test
    void emptyAvailableSourceDiffersFromPolicyOmission() {
        var available = EquipmentLifecycleContextV1.SectionMetadata.available(
                EquipmentLifecycleDataQuality.Availability.AVAILABLE_BUT_OPTIONAL,
                List.of("condition_readings"), 0, false, null, null, null);
        var omitted = EquipmentLifecycleContextV1.SectionMetadata.unavailable(
                EquipmentLifecycleDataQuality.Availability.OUT_OF_SCOPE_FOR_V1,
                EquipmentLifecycleDataQuality.SourceReliability.OUT_OF_SCOPE,
                List.of());

        assertThat(available.availability()).isNotEqualTo(omitted.availability());
        assertThat(available.returnedCount()).isZero();
        assertThat(omitted.returnedCount()).isZero();
    }

    @Test
    void syntheticExampleUsesV1AndContainsNoForbiddenPayloadFields() throws Exception {
        Path examplePath = Path.of(
                "docs/ai/equipment-lifecycle/equipment-lifecycle-context-v1.example.json");
        JsonNode example = mapper.readTree(Files.readString(examplePath));
        String serialized = mapper.writeValueAsString(example);

        assertThat(example.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(serialized)
                .doesNotContain(
                        "phone",
                        "email",
                        "token",
                        "signedUrl",
                        "binaryContent",
                        "uploadedBy",
                        "reporterId",
                        "performer");
        assertThat(example.path("hierarchy").path("metadata").path("truncated").asBoolean())
                .isTrue();
        assertThat(example.path("dataQuality").path("issues").isArray()).isTrue();
    }

    @Test
    void schemaAndSyntheticExampleDeclareSameVersion() throws Exception {
        JsonNode schema = mapper.readTree(Files.readString(Path.of(
                "docs/ai/equipment-lifecycle/equipment-lifecycle-context-v1.schema.json")));
        JsonNode example = mapper.readTree(Files.readString(Path.of(
                "docs/ai/equipment-lifecycle/equipment-lifecycle-context-v1.example.json")));

        assertThat(schema.path("properties").path("schemaVersion")
                .path("const").asText()).isEqualTo(example.path("schemaVersion").asText());
        assertThat(StreamSupport.stream(
                schema.path("required").spliterator(), false)
                .map(JsonNode::asText))
                .contains("dataQuality");
        assertThat(example.path("asOf").asText())
                .isEqualTo(Instant.parse(example.path("asOf").asText()).toString());
    }

    @Test
    void defectRepairDemandAndConditionSignalRemainSeparateContractSections() {
        Set<String> topLevelFields = Arrays.stream(
                        EquipmentLifecycleContextV1.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(topLevelFields)
                .contains("defects", "repairRequests", "conditionMeasurements")
                .doesNotContain("failureCount", "incidents");
    }

    @Test
    void contractRecordsDoNotExposePrivateContactsActorsOrBinaryLocations() {
        List<Class<?>> records = List.of(
                EquipmentLifecycleContextV1.class,
                EquipmentLifecycleContextV1.EquipmentCore.class,
                EquipmentLifecycleContextV1.Passport.class,
                EquipmentLifecycleContextV1.LifecycleEvent.class,
                EquipmentLifecycleContextV1.MeterReadingItem.class,
                EquipmentLifecycleContextV1.WorkOrderItem.class,
                EquipmentLifecycleContextV1.RepairRequestItem.class,
                EquipmentLifecycleContextV1.InspectionItem.class,
                EquipmentLifecycleContextV1.InstalledComponentItem.class,
                EquipmentLifecycleContextV1.ComponentReplacementItem.class,
                EquipmentLifecycleContextV1.DocumentMetadata.class);
        String componentNames = records.stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(component -> component.getName().toLowerCase())
                .collect(java.util.stream.Collectors.joining(","));

        assertThat(componentNames)
                .doesNotContain(
                        "phone",
                        "email",
                        "token",
                        "password",
                        "signedurl",
                        "binary",
                        "fileid",
                        "uploadedby",
                        "reporterid",
                        "performer",
                        "recordedby",
                        "installedby",
                        "removedby");
    }
}

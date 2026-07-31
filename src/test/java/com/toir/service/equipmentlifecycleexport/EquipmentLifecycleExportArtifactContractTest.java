package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportResponses;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentLifecycleExportArtifactContractTest {
    private static final Path CANONICAL_SCHEMA = Path.of(
            "docs/ai/equipment-lifecycle/equipment-lifecycle-context-v1.schema.json");
    private static final Path MANIFEST_SCHEMA = Path.of(
            "docs/ai/equipment-lifecycle/equipment-lifecycle-export-manifest-v1.schema.json");
    private static final Path MANIFEST_EXAMPLE = Path.of(
            "docs/ai/equipment-lifecycle/equipment-lifecycle-export-manifest-v1.example.json");

    @Test
    void canonicalW1SchemaIsPackagedFromItsSingleAuthoritativeSource() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(Files.readString(CANONICAL_SCHEMA)).isNotBlank();
        assertThat(pom).contains(
                "<directory>docs/ai/equipment-lifecycle</directory>",
                "<include>equipment-lifecycle-context-v1.schema.json</include>",
                "<targetPath>ai/equipment-lifecycle</targetPath>"
        );
        assertThat(Files.exists(Path.of(
                "src/main/resources/ai/equipment-lifecycle/equipment-lifecycle-context-v1.schema.json"))).isFalse();
    }

    @Test
    void manifestSchemaAndSyntheticExampleAreValidJsonWithMatchingV1Constants() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var schema = mapper.readTree(Files.readString(MANIFEST_SCHEMA));
        var example = mapper.readTree(Files.readString(MANIFEST_EXAMPLE));

        assertThat(schema.path("properties").path("manifestVersion").path("const").asText()).isEqualTo("1.0");
        assertThat(example.path("manifestVersion").asText()).isEqualTo("1.0");
        assertThat(example.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(example.path("consistency").path("equipmentMembershipFrozen").asBoolean()).isTrue();
        assertThat(example.path("consistency").path("singleDatabaseSnapshot").asBoolean()).isFalse();
        assertThat(Files.readString(MANIFEST_EXAMPLE)).doesNotContain(
                "email", "phone", "token", "secret", "objectKey", "signedUrl", "/home/");
    }

    @Test
    void ordinaryApiDtosCannotExposeStorageKeysPathsOrUrls() {
        java.util.List<String> names = java.util.stream.Stream.of(
                        EquipmentLifecycleExportResponses.JobResponse.class,
                        EquipmentLifecycleExportResponses.ArtifactDescriptor.class)
                .flatMap(type -> java.util.Arrays.stream(type.getRecordComponents()))
                .map(java.lang.reflect.RecordComponent::getName)
                .map(String::toLowerCase)
                .toList();

        assertThat(names).noneMatch(name -> name.contains("objectkey")
                || name.contains("storagepath")
                || name.contains("signedurl")
                || name.contains("credential")
                || name.contains("idempotency"));
    }
}

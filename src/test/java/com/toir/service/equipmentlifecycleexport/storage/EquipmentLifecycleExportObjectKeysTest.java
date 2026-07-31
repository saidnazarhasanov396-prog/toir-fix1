package com.toir.service.equipmentlifecycleexport.storage;

import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipmentLifecycleExportObjectKeysTest {

    private final EquipmentLifecycleExportObjectKeys keys =
            new EquipmentLifecycleExportObjectKeys("equipment-lifecycle-exports/");

    @Test
    void partKeyIsServerGeneratedAttemptScopedAndDeterministic() {
        UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID token = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThat(keys.part(jobId, token, 7, 300, 349)).isEqualTo(
                "equipment-lifecycle-exports/staging/11111111-1111-1111-1111-111111111111/"
                        + "attempt-22222222-2222-2222-2222-222222222222/"
                        + "parts/part-00000007-ord-000000000300-000000000349.ndjson"
        );
    }

    @Test
    void finalKeyUsesOnlyAllowlistedArtifactFilename() {
        UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID token = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThat(keys.finalArtifact(jobId, token, EquipmentLifecycleExportArtifactType.MANIFEST)).isEqualTo(
                "equipment-lifecycle-exports/final/11111111-1111-1111-1111-111111111111/"
                        + "attempt-22222222-2222-2222-2222-222222222222/"
                        + "equipment-lifecycle-export-manifest-v1.json"
        );
    }

    @Test
    void prefixRejectsTraversalAbsoluteAndBackslashSegments() {
        assertThatThrownBy(() -> new EquipmentLifecycleExportObjectKeys("../exports"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EquipmentLifecycleExportObjectKeys("/exports"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EquipmentLifecycleExportObjectKeys("exports\\private"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesThatStorageKeysRemainUnderConfiguredPrefix() {
        assertThatThrownBy(() -> keys.requireManaged("other/job/file"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keys.requireManaged("equipment-lifecycle-exports/../secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(keys.requireManaged("equipment-lifecycle-exports/final/a/file.json"))
                .isEqualTo("equipment-lifecycle-exports/final/a/file.json");
    }
}

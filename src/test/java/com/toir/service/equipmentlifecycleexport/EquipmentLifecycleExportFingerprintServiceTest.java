package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentLifecycleExportFingerprintServiceTest {

    @Test
    void requestFingerprintDeduplicatesAndSortsExplicitIds() {
        EquipmentLifecycleExportFingerprintService service =
                new EquipmentLifecycleExportFingerprintService(new ObjectMapper());
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        var left = service.normalizeRequest("standard-v1", EquipmentLifecycleExportScopeMode.EXPLICIT_IDS,
                List.of(second, first, second));
        var right = service.normalizeRequest("standard-v1", EquipmentLifecycleExportScopeMode.EXPLICIT_IDS,
                List.of(first, second));

        assertThat(left.json()).isEqualTo(right.json());
        assertThat(left.fingerprint()).isEqualTo(right.fingerprint());
    }

    @Test
    void requestFingerprintChangesWhenScopeChanges() {
        EquipmentLifecycleExportFingerprintService service =
                new EquipmentLifecycleExportFingerprintService(new ObjectMapper());

        String explicit = service.normalizeRequest("standard-v1", EquipmentLifecycleExportScopeMode.EXPLICIT_IDS,
                List.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))).fingerprint();
        String all = service.normalizeRequest("standard-v1", EquipmentLifecycleExportScopeMode.ALL_AUTHORIZED,
                List.of()).fingerprint();

        assertThat(explicit).isNotEqualTo(all);
    }
}

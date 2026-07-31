package com.toir.service.equipmentlifecycleexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.config.EquipmentLifecycleExportProperties;
import com.toir.entity.equipmentlifecycleexport.EquipmentLifecycleExportPart;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import com.toir.repository.equipmentlifecycleexport.EquipmentLifecycleExportPartRepository;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportStorage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipmentLifecycleExportFinalizerTest {

    @Test
    void sha256SumsUsesDeterministicConventionalFormatAndDoesNotHashItself() {
        String text = new String(EquipmentLifecycleExportFinalizer.checksumBytes(
                "a".repeat(64), "b".repeat(64), "c".repeat(64)), StandardCharsets.UTF_8);

        assertThat(text).isEqualTo(
                "a".repeat(64) + "  equipment-lifecycle-context-v1.ndjson\n"
                        + "b".repeat(64) + "  equipment-lifecycle-context-v1.schema.json\n"
                        + "c".repeat(64) + "  equipment-lifecycle-export-manifest-v1.json\n");
        assertThat(text).doesNotContain("SHA256SUMS");
    }

    @Test
    void recordCountMismatchBlocksFinalizationBeforeAnyCompletedTransition() {
        UUID jobId = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        EquipmentLifecycleExportPartRepository parts = mock(EquipmentLifecycleExportPartRepository.class);
        EquipmentLifecycleExportPart part = new EquipmentLifecycleExportPart();
        part.setPartNumber(0);
        part.setFirstOrdinal(0);
        part.setLastOrdinal(0);
        part.setRecordCount(1);
        part.setObjectKey("exports/staging/part");
        part.setObjectSize(3);
        part.setSha256("a".repeat(64));
        when(parts.findAllByJobIdOrderByPartNumberAsc(jobId)).thenReturn(List.of(part));
        EquipmentLifecycleExportLeaseService leases = mock(EquipmentLifecycleExportLeaseService.class);
        Instant at = Instant.parse("2026-07-31T10:00:00Z");
        when(leases.ownedJob(jobId, token)).thenReturn(new EquipmentLifecycleExportLeaseService.OwnedJob(
                jobId, at, "{}", "b".repeat(64), "c".repeat(64), "standard-v1",
                EquipmentLifecycleExportScopeMode.EXPLICIT_IDS, true, 2, 1, 0, null, at, at));
        EquipmentLifecycleExportStorage storage = mock(EquipmentLifecycleExportStorage.class);
        when(storage.head(part.getObjectKey())).thenReturn(Optional.of(
                new EquipmentLifecycleExportStorage.ObjectMetadata(part.getObjectKey(), 3, part.getSha256())));
        EquipmentLifecycleExportProperties properties = new EquipmentLifecycleExportProperties();
        properties.getS3().setPrefix("exports/");
        EquipmentLifecycleExportFinalizer finalizer = new EquipmentLifecycleExportFinalizer(
                parts, leases, storage, properties, new ObjectMapper());

        assertThatThrownBy(() -> finalizer.finalizeExport(jobId, token))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("record count");
        verify(leases, never()).beginFinalizing(jobId, token);
        verify(leases, never()).complete(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}

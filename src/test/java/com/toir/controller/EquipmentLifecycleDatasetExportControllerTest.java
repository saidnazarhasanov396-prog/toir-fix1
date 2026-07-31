package com.toir.controller;

import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType;
import com.toir.security.AuthenticatedUser;
import com.toir.service.equipmentlifecycleexport.EquipmentLifecycleExportJobService;
import com.toir.service.equipmentlifecycleexport.storage.EquipmentLifecycleExportStorage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquipmentLifecycleDatasetExportControllerTest {

    @Test
    void downloadReturnsStreamingResourceAndFixedPrivateSecurityHeaders() {
        EquipmentLifecycleExportJobService service = mock(EquipmentLifecycleExportJobService.class);
        EquipmentLifecycleDatasetExportController controller = new EquipmentLifecycleDatasetExportController(service);
        UUID jobId = UUID.randomUUID();
        byte[] bytes = "{}\n".getBytes(StandardCharsets.UTF_8);
        String sha = EquipmentLifecycleExportStorage.sha256(bytes);
        var stored = new EquipmentLifecycleExportStorage.StoredObject(
                new ByteArrayInputStream(bytes),
                new EquipmentLifecycleExportStorage.ObjectMetadata("exports/final/private", bytes.length, sha)
        );
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID().toString(), "admin", null,
                null, null, "SYSTEM_ADMIN", List.of("*"));
        when(service.download(jobId, EquipmentLifecycleExportArtifactType.DATASET, user))
                .thenReturn(new EquipmentLifecycleExportJobService.ArtifactDownload(
                        EquipmentLifecycleExportArtifactType.DATASET, stored));

        var response = controller.download(jobId, EquipmentLifecycleExportArtifactType.DATASET, user);

        assertThat(response.getBody()).isInstanceOf(InputStreamResource.class);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(bytes.length);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"sha256-" + sha + "\"");
        assertThat(response.getHeaders().getCacheControl()).contains("private", "no-store", "no-transform");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("equipment-lifecycle-context-v1.ndjson");
    }
}

package com.toir.controller;

import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportRequests.CreateRequest;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportResponses.ArtifactDescriptor;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportResponses.JobResponse;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportArtifactType;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.equipmentlifecycleexport.EquipmentLifecycleExportJobService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/equipment-lifecycle/dataset-exports")
@ConditionalOnProperty(prefix = "toir.ai.equipment-lifecycle.export", name = "enabled", havingValue = "true")
@PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_LIFECYCLE_DATASET_EXPORT')")
public class EquipmentLifecycleDatasetExportController {
    private final EquipmentLifecycleExportJobService service;

    public EquipmentLifecycleDatasetExportController(EquipmentLifecycleExportJobService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<JobResponse> create(
            @CurrentUser AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRequest request
    ) {
        JobResponse response = service.create(user, idempotencyKey, request);
        return ResponseEntity.accepted()
                .location(URI.create(response.statusUrl()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<Page<JobResponse>> list(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) EquipmentLifecycleExportStatus status
    ) {
        return ResponseEntity.ok(service.list(user, page, size, status));
    }

    @GetMapping("/{exportId}")
    public ResponseEntity<JobResponse> status(
            @PathVariable UUID exportId,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.get(exportId, user));
    }

    @PostMapping("/{exportId}/resume")
    public ResponseEntity<JobResponse> resume(
            @PathVariable UUID exportId,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.accepted().body(service.resume(exportId, user));
    }

    @PostMapping("/{exportId}/cancel")
    public ResponseEntity<JobResponse> cancel(
            @PathVariable UUID exportId,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.accepted().body(service.cancel(exportId, user));
    }

    @GetMapping("/{exportId}/artifacts")
    public ResponseEntity<List<ArtifactDescriptor>> artifacts(
            @PathVariable UUID exportId,
            @CurrentUser AuthenticatedUser user
    ) {
        return ResponseEntity.ok(service.artifacts(exportId, user));
    }

    @GetMapping("/{exportId}/artifacts/{artifactType}")
    public ResponseEntity<Resource> download(
            @PathVariable UUID exportId,
            @PathVariable EquipmentLifecycleExportArtifactType artifactType,
            @CurrentUser AuthenticatedUser user
    ) {
        EquipmentLifecycleExportJobService.ArtifactDownload download =
                service.download(exportId, artifactType, user);
        var metadata = download.object().metadata();
        InputStreamResource resource = new InputStreamResource(download.object().input());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.type().getMediaType()))
                .contentLength(metadata.size())
                .eTag("\"sha256-" + metadata.sha256() + "\"")
                .cacheControl(CacheControl.noStore().cachePrivate().noTransform())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.type().getFilename(), StandardCharsets.UTF_8)
                        .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}

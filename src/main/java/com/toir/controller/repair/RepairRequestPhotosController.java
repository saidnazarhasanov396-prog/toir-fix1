package com.toir.controller.repair;
import com.toir.dto.file.FileAssetDto;
import com.toir.entity.FileAsset;
import com.toir.exception.RestException;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.FileAssetService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photo attachment endpoints for a specific repair request.
 * Uses the generic FileAsset storage with entityType=RepairRequest.
 */
@RestController
@RequestMapping("/api/v1/repair-requests/{requestId}/photos")
@Tag(name = "repair-request-photos")
@RequiredArgsConstructor
public class RepairRequestPhotosController {

    private static final String ENTITY_TYPE = "RepairRequest";

    private final FileAssetService fileAssetService;
    private final RepairRequestRepository repairRequestRepository;


    @GetMapping
    public ResponseEntity<Page<FileAssetDto>> list(@PathVariable UUID requestId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @CurrentUser AuthenticatedUser user) {
        ensureExists(requestId);
        return ResponseEntity.ok(PaginationUtils.page(fileAssetService.findByEntity(ENTITY_TYPE, requestId.toString(), user), page, size)
                .map(asset -> asset.withDownloadUrl(photoDownloadUrl(requestId, asset.id()))));
    }

    @PostMapping
    public ResponseEntity<FileAssetDto> upload(
            @PathVariable UUID requestId,
            @RequestParam("file") MultipartFile file,
            @CurrentUser AuthenticatedUser user
    ) {
        ensureExists(requestId);
        UUID uploaderId = user != null ? UUID.fromString(user.id()) : null;
        FileAssetDto asset = fileAssetService.upload(file, ENTITY_TYPE, requestId.toString(), uploaderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(asset.withDownloadUrl(photoDownloadUrl(requestId, asset.id())));
    }

    @GetMapping("/{photoId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID requestId,
                                             @PathVariable UUID photoId,
                                             @CurrentUser AuthenticatedUser user) {
        ensureExists(requestId);
        FileAsset asset = fileAssetService.findAssetByEntity(ENTITY_TYPE, requestId.toString(), photoId, user);
        Resource resource = fileAssetService.downloadForEntity(ENTITY_TYPE, requestId.toString(), photoId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(asset.getOriginalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    private void ensureExists(UUID requestId) {
        if (!repairRequestRepository.existsByIdAndIsDeletedFalse(requestId)) {
            throw RestException.notFound("Repair request not found: " + requestId);
        }
    }

    private String photoDownloadUrl(UUID requestId, UUID photoId) {
        return "/api/v1/repair-requests/" + requestId + "/photos/" + photoId + "/download";
    }
}

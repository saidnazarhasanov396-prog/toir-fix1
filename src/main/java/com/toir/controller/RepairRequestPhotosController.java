package com.toir.controller;
import com.toir.entity.FileAsset;
import com.toir.entity.RepairRequest;
import com.toir.repository.RepairRequestRepository;

import com.toir.exception.RestException;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.FileAssetService;
import com.toir.dto.file.FileAssetDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Photo attachment endpoints for a specific repair request.
 * Uses the generic FileAsset storage with entityType=RepairRequest.
 */
@RestController
@RequestMapping("/api/v1/repair-requests/{requestId}/photos")
@Tag(name = "repair-request-photos")
public class RepairRequestPhotosController {

    private static final String ENTITY_TYPE = "RepairRequest";

    private final FileAssetService fileAssetService;
    private final RepairRequestRepository repairRequestRepository;

    public RepairRequestPhotosController(FileAssetService fileAssetService,
                                         RepairRequestRepository repairRequestRepository) {
        this.fileAssetService = fileAssetService;
        this.repairRequestRepository = repairRequestRepository;
    }

    @GetMapping
    public List<FileAssetDto> list(@PathVariable UUID requestId) {
        ensureExists(requestId);
        return fileAssetService.findByEntity(ENTITY_TYPE, requestId.toString());
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
        return ResponseEntity.status(HttpStatus.CREATED).body(asset);
    }

    private void ensureExists(UUID requestId) {
        if (!repairRequestRepository.existsById(requestId)) {
            throw RestException.notFound("Repair request not found: " + requestId);
        }
    }
}

package com.toir.controller;
import com.toir.dto.file.FileAssetDto;
import com.toir.dto.technicaldocument.TechnicalDocumentDto;
import com.toir.entity.FileAsset;
import com.toir.enums.DocumentType;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.TechnicalDocumentRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.FileAssetService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "files")
@RequiredArgsConstructor
public class FileAssetController {

    private final FileAssetService service;
    private final FileAssetRepository repository;
    private final TechnicalDocumentRepository technicalDocumentRepository;

    @GetMapping
    public ResponseEntity<Page<FileAssetDto>> list(@RequestParam String entityType, @RequestParam String entityId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByEntity(entityType, entityId), page, size));
    }

    @GetMapping("/assets")
    public ResponseEntity<Page<FileAssetDto>> legacyAssets(@RequestParam(required = false) String entityType,
                                           @RequestParam(required = false) String entityId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        if (entityType != null && entityId != null) {
            return ResponseEntity.ok(PaginationUtils.page(service.findByEntity(entityType, entityId), page, size));
        }
        return ResponseEntity.ok(PaginationUtils.page(repository.findAllByIsDeletedFalseOrderByCreatedAtDesc().stream().map(FileAssetDto::from).toList(), page, size));
    }

    @GetMapping("/documents")
    public ResponseEntity<Page<TechnicalDocumentDto>> legacyDocuments(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(technicalDocumentRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(TechnicalDocumentDto::from).toList(), page, size));
    }

    @GetMapping("/document-types")
    public ResponseEntity<List<String>> documentTypes() {
        return ResponseEntity.ok(Arrays.stream(DocumentType.values()).map(Enum::name).toList());
    }

    @PostMapping("/upload")
    public ResponseEntity<FileAssetDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String entityType,
            @RequestParam String entityId,
            @CurrentUser AuthenticatedUser user
    ) {
        UUID uploaderId = user != null ? UUID.fromString(user.id()) : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.upload(file, entityType, entityId, uploaderId));
    }

    @PostMapping("/assets/upload")
    public ResponseEntity<FileAssetDto> legacyUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @CurrentUser AuthenticatedUser user
    ) {
        UUID uploaderId = user != null ? UUID.fromString(user.id()) : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.upload(file, entityType != null ? entityType : "misc",
                        entityId != null ? entityId : "", uploaderId));
    }

    @GetMapping("/assets/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        FileAsset asset = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> com.toir.exception.RestException.notFound("File not found: " + id));
        Resource resource = service.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(asset.getOriginalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

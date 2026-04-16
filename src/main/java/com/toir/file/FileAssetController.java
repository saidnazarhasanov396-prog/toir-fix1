package com.toir.file;

import com.toir.common.security.AuthenticatedUser;
import com.toir.common.security.CurrentUser;
import com.toir.file.dto.FileAssetDto;
import com.toir.technicaldocument.TechnicalDocumentRepository;
import com.toir.technicaldocument.dto.TechnicalDocumentDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/files")
@Tag(name = "files")
public class FileAssetController {

    private final FileAssetService service;
    private final FileAssetRepository repository;
    private final TechnicalDocumentRepository technicalDocumentRepository;

    public FileAssetController(FileAssetService service,
                               FileAssetRepository repository,
                               TechnicalDocumentRepository technicalDocumentRepository) {
        this.service = service;
        this.repository = repository;
        this.technicalDocumentRepository = technicalDocumentRepository;
    }

    @GetMapping
    public List<FileAssetDto> list(@RequestParam String entityType, @RequestParam String entityId) {
        return service.findByEntity(entityType, entityId);
    }

    @GetMapping("/assets")
    public List<FileAssetDto> legacyAssets(@RequestParam(required = false) String entityType,
                                           @RequestParam(required = false) String entityId) {
        if (entityType != null && entityId != null) {
            return service.findByEntity(entityType, entityId);
        }
        return repository.findAll().stream().map(FileAssetDto::from).toList();
    }

    @GetMapping("/documents")
    public List<TechnicalDocumentDto> legacyDocuments() {
        return technicalDocumentRepository.findAll().stream().map(TechnicalDocumentDto::from).toList();
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
        FileAsset asset = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));
        try {
            Resource resource = new UrlResource(Path.of(asset.getStoragePath()).toUri());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(asset.getMimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + asset.getOriginalName() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Cannot read file", e);
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}

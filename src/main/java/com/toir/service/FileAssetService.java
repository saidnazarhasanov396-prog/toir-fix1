package com.toir.service;

import com.toir.dto.file.FileAssetDto;
import com.toir.entity.FileAsset;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.FileAssetRepository;
import com.toir.service.file_management.FileValidator;
import com.toir.service.file_management.LocalFileResourceResolver;
import com.toir.util.AuditBuilderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class FileAssetService {

    private final FileAssetRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final LocalFileResourceResolver localFileResourceResolver;
    private final FileValidator fileValidator;
    private final Path storageRoot;

    public FileAssetService(FileAssetRepository repository,
                            AuditBuilderService auditBuilderService,
                            LocalFileResourceResolver localFileResourceResolver,
                            FileValidator fileValidator,
                            @Value("${app.files.storage-path:uploads}") String storagePath) {
        this.repository = repository;
        this.auditBuilderService = auditBuilderService;
        this.localFileResourceResolver = localFileResourceResolver;
        this.fileValidator = fileValidator;
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage dir: " + storageRoot, e);
        }
    }

    @Transactional(readOnly = true)
    public List<FileAssetDto> findByEntity(String entityType, String entityId) {
        return repository.findAllByEntityTypeAndEntityIdAndIsDeletedFalse(entityType, entityId).stream()
                .map(FileAssetDto::from).toList();
    }

    @Transactional(readOnly = true)
    public FileAsset findAssetByEntity(String entityType, String entityId, UUID id) {
        FileAsset asset = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("File not found: " + id));
        if (!matchesEntity(asset, entityType, entityId)) {
            throw RestException.notFound("File not found: " + id);
        }
        return asset;
    }

    @Transactional
    public FileAssetDto upload(MultipartFile file, String entityType, String entityId, UUID uploadedById) {
        if (file == null || file.isEmpty()) {
            throw RestException.badRequest("File is required");
        }
        FileValidator.ValidatedFile validated = fileValidator.validate(file);
        String storedName = UUID.randomUUID() + "." + validated.extension();
        Path target = storageRoot.resolve(storedName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw RestException.badRequest("Invalid file path");
        }
        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
        FileAsset asset = new FileAsset();
        asset.setFileName(storedName);
        asset.setOriginalName(validated.originalName());
        asset.setMimeType(validated.contentType());
        asset.setSizeBytes(validated.size());
        asset.setStoragePath(target.toString());
        asset.setEntityType(entityType);
        asset.setEntityId(entityId);
        asset.setUploadedById(uploadedById);
        FileAsset saved = repository.save(asset);

        auditBuilderService.log(
                "file_asset",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.FILE_ASSET,
                "Файл загружен",
                null,
                saved
        );

        return FileAssetDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Resource download(UUID id) {
        FileAsset asset = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("File not found: " + id));
        return load(asset);
    }

    @Transactional(readOnly = true)
    public Resource downloadForEntity(String entityType, String entityId, UUID id) {
        return load(findAssetByEntity(entityType, entityId, id));
    }

    @Transactional
    public void delete(UUID id) {
        FileAsset asset = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("File not found: " + id));
        try {
            Files.deleteIfExists(Paths.get(asset.getStoragePath()));
        } catch (IOException ignored) {
        }
        asset.setDeleted(true);
        FileAsset saved = repository.save(asset);

        auditBuilderService.log(
                "file_asset",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.FILE_ASSET,
                "Файл удален",
                saved,
                null

        );

    }


    private Resource load(FileAsset asset) {
        return localFileResourceResolver.load(asset.getStoragePath());
    }

    private boolean matchesEntity(FileAsset asset, String entityType, String entityId) {
        return asset != null
                && java.util.Objects.equals(asset.getEntityType(), entityType)
                && java.util.Objects.equals(asset.getEntityId(), entityId);
    }
}

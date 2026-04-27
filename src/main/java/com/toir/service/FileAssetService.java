package com.toir.service;
import com.toir.entity.FileAsset;
import com.toir.repository.FileAssetRepository;

import com.toir.exception.RestException;
import com.toir.dto.file.FileAssetDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
@Transactional
public class FileAssetService {

    private final FileAssetRepository repository;
    private final Path storageRoot;

    public FileAssetService(FileAssetRepository repository,
                            @Value("${app.files.storage-path:uploads}") String storagePath) {
        this.repository = repository;
        this.storageRoot = Paths.get(storagePath).toAbsolutePath();
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

    public FileAssetDto upload(MultipartFile file, String entityType, String entityId, UUID uploadedById) {
        if (file == null || file.isEmpty()) {
            throw RestException.badRequest("File is required");
        }
        String storedName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path target = storageRoot.resolve(storedName);
        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
        FileAsset asset = new FileAsset();
        asset.setFileName(storedName);
        asset.setOriginalName(file.getOriginalFilename());
        asset.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        asset.setSizeBytes(file.getSize());
        asset.setStoragePath(target.toString());
        asset.setEntityType(entityType);
        asset.setEntityId(entityId);
        asset.setUploadedById(uploadedById);
        return FileAssetDto.from(repository.save(asset));
    }

    public void delete(UUID id) {
        FileAsset asset = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("File not found: " + id));
        try {
            Files.deleteIfExists(Paths.get(asset.getStoragePath()));
        } catch (IOException ignored) {
        }
        asset.setDeleted(true);
        repository.save(asset);
    }
}

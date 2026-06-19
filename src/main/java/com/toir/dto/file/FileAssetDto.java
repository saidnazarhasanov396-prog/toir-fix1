package com.toir.dto.file;

import com.toir.entity.FileAsset;

import java.time.Instant;
import java.util.UUID;

public record FileAssetDto(
        UUID id,
        String fileName,
        String originalName,
        String mimeType,
        long sizeBytes,
        String entityType,
        String entityId,
        UUID uploadedById,
        Instant createdAt,
        String downloadUrl
) {
    public static FileAssetDto from(FileAsset f) {
        return from(f, "/api/v1/files/assets/" + f.getId() + "/download");
    }

    public static FileAssetDto from(FileAsset f, String downloadUrl) {
        return new FileAssetDto(f.getId(), f.getFileName(), f.getOriginalName(), f.getMimeType(),
                f.getSizeBytes(), f.getEntityType(), f.getEntityId(), f.getUploadedById(), f.getCreatedAt(), downloadUrl);
    }

    public FileAssetDto withDownloadUrl(String downloadUrl) {
        return new FileAssetDto(id, fileName, originalName, mimeType, sizeBytes, entityType, entityId,
                uploadedById, createdAt, downloadUrl);
    }
}

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
        Instant createdAt
) {
    public static FileAssetDto from(FileAsset f) {
        return new FileAssetDto(f.getId(), f.getFileName(), f.getOriginalName(), f.getMimeType(),
                f.getSizeBytes(), f.getEntityType(), f.getEntityId(), f.getUploadedById(), f.getCreatedAt());
    }
}

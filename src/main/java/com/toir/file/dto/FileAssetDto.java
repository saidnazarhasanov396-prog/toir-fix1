package com.toir.file.dto;

import com.toir.file.FileAsset;

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

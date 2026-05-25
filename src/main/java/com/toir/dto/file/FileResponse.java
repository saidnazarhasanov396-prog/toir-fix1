package com.toir.dto.file;

import com.toir.entity.UploadedFile;
import com.toir.enums.FileCategory;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record FileResponse(
        UUID id,
        String originalName,
        String storedName,
        String url,
        String contentType,
        String extension,
        Long size,
        UUID uploadedBy,
        FileCategory category,
        Boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {
    public static FileResponse from(UploadedFile file) {
        return FileResponse.builder()
                .id(file.getId())
                .originalName(file.getOriginalName())
                .storedName(file.getStoredName())
                .url(file.getUrl())
                .contentType(file.getContentType())
                .extension(file.getExtension())
                .size(file.getSize())
                .uploadedBy(file.getUploadedBy())
                .category(file.getCategory())
                .deleted(file.getDeleted())
                .createdAt(file.getCreatedAt())
                .deletedAt(file.getDeletedAt())
                .build();
    }
}

package com.toir.dto.file;

import com.toir.entity.UploadedFile;
import com.toir.enums.FileCategory;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UploadFileResponse(
        UUID id,
        String originalName,
        String storedName,
        String url,
        String contentType,
        String extension,
        Long size,
        FileCategory category,
        LocalDateTime createdAt
) {
    public static UploadFileResponse from(UploadedFile file) {
        return UploadFileResponse.builder()
                .id(file.getId())
                .originalName(file.getOriginalName())
                .storedName(file.getStoredName())
                .url(file.getUrl())
                .contentType(file.getContentType())
                .extension(file.getExtension())
                .size(file.getSize())
                .category(file.getCategory())
                .createdAt(file.getCreatedAt())
                .build();
    }
}

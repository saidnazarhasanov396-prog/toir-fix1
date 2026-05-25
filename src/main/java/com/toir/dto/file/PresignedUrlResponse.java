package com.toir.dto.file;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record PresignedUrlResponse(
        UUID fileId,
        String url,
        LocalDateTime expiresAt
) {
}

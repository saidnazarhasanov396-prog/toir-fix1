package com.toir.dto.stockmovement;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.entity.StockMovementFile;
import com.toir.entity.UploadedFile;

import java.util.UUID;

public record StockMovementFileDto(
        UUID id,
        String originalName,
        String contentType,
        Long size,
        String downloadUrl
) {
    public static StockMovementFileDto from(UUID movementId, StockMovementFile link) {
        if (link == null || link.getFile() == null) {
            return null;
        }
        UploadedFile file = link.getFile();
        return new StockMovementFileDto(
                file.getId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                "/api/v1/stock-movements/" + movementId + "/files/" + file.getId() + "/download"
        );
    }

    public static StockMovementFileDto fromAttachmentFile(UUID movementId, AttachmentGroupDto.FileItem file) {
        if (file == null) {
            return null;
        }
        return new StockMovementFileDto(
                file.fileId(),
                file.originalName(),
                file.contentType(),
                file.size(),
                "/api/v1/stock-movements/" + movementId + "/files/" + file.fileId() + "/download"
        );
    }
}

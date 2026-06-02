package com.toir.dto.equipment;

import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.EquipmentDocument;

import java.time.LocalDateTime;
import java.util.UUID;

public record EquipmentDocumentDto(
        UUID id,
        UUID fileId,
        String documentType,
        String documentName,
        String originalName,
        String contentType,
        Long size,
        String downloadUrl,
        String presignedUrlEndpoint,
        LocalDateTime createdAt,
        LocalDateTime uploadedAt
) {
    public static EquipmentDocumentDto from(UUID equipmentId, EquipmentDocument document) {
        if (document == null || document.getFile() == null || Boolean.TRUE.equals(document.getFile().getDeleted())) {
            return null;
        }
        UploadedFile file = document.getFile();
        return new EquipmentDocumentDto(
                document.getId(),
                file.getId(),
                document.getDocumentType(),
                document.getDocumentName(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                "/api/v1/equipment/" + equipmentId + "/documents/" + document.getId() + "/download",
                "/api/v1/equipment/" + equipmentId + "/documents/" + document.getId() + "/presigned-url",
                document.getCreatedAt(),
                document.getCreatedAt()
        );
    }
}

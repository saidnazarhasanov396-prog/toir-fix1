package com.toir.dto.vehicle;

import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.VehicleDocument;

import java.time.LocalDateTime;
import java.util.UUID;

public record VehicleDocumentDto(
        UUID id,
        UUID fileId,
        String documentType,
        String originalName,
        String contentType,
        Long size,
        String downloadUrl,
        String presignedUrlEndpoint,
        LocalDateTime createdAt
) {
    public static VehicleDocumentDto from(UUID equipmentId, VehicleDocument document) {
        if (document == null || document.getFile() == null || Boolean.TRUE.equals(document.getFile().getDeleted())) {
            return null;
        }
        UploadedFile file = document.getFile();
        return new VehicleDocumentDto(
                document.getId(),
                file.getId(),
                document.getDocumentType(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                "/api/files/" + file.getId() + "/download",
                "/api/v1/vehicles/" + equipmentId + "/documents/" + document.getId() + "/presigned-url",
                document.getCreatedAt()
        );
    }
}

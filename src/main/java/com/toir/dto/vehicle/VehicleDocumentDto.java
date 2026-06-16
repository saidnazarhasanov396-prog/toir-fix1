package com.toir.dto.vehicle;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.VehicleDocument;

import java.time.LocalDateTime;
import java.util.UUID;

public record VehicleDocumentDto(
        UUID id,
        UUID fileId,
        String documentType,
        String documentNumber,
        String documentName,
        String originalName,
        String contentType,
        Long size,
        String downloadUrl,
        String presignedUrlEndpoint,
        LocalDateTime createdAt,
        LocalDateTime uploadedAt,
        UUID equipmentId,
        String title,
        String type,
        LocalDateTime documentDate,
        FileRef file
) {
    public record FileRef(
            UUID id,
            String fileName,
            String originalName,
            String mimeType,
            Long sizeBytes,
            String downloadUrl
    ) {}

    public VehicleDocumentDto(
            UUID id,
            UUID fileId,
            String documentType,
            String documentNumber,
            String documentName,
            String originalName,
            String contentType,
            Long size,
            String downloadUrl,
            String presignedUrlEndpoint,
            LocalDateTime createdAt,
            LocalDateTime uploadedAt
    ) {
        this(id, fileId, documentType, documentNumber, documentName, originalName, contentType, size, downloadUrl,
                presignedUrlEndpoint, createdAt, uploadedAt, null, documentName, documentType, createdAt, null);
    }

    public static VehicleDocumentDto from(UUID equipmentId, VehicleDocument document) {
        if (document == null || document.getFile() == null || Boolean.TRUE.equals(document.getFile().getDeleted())) {
            return null;
        }
        UploadedFile file = document.getFile();
        return new VehicleDocumentDto(
                document.getId(),
                file.getId(),
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.getDocumentName(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                "/api/v1/vehicles/" + equipmentId + "/documents/" + document.getId() + "/download",
                "/api/v1/vehicles/" + equipmentId + "/documents/" + document.getId() + "/presigned-url",
                document.getCreatedAt(),
                document.getCreatedAt(),
                equipmentId,
                document.getDocumentName(),
                document.getDocumentType(),
                document.getCreatedAt(),
                new FileRef(
                        file.getId(),
                        file.getStoredName(),
                        file.getOriginalName(),
                        file.getContentType(),
                        file.getSize(),
                        "/api/v1/vehicles/" + equipmentId + "/documents/" + document.getId() + "/download"
                )
        );
    }

    public static VehicleDocumentDto fromAttachmentGroup(UUID equipmentId, AttachmentGroupDto group) {
        if (group == null || group.files() == null || group.files().isEmpty()) {
            return null;
        }
        AttachmentGroupDto.FileItem file = group.files().getFirst();
        String downloadUrl = "/api/v1/vehicles/" + equipmentId + "/documents/" + group.id() + "/download";
        return new VehicleDocumentDto(
                group.id(),
                file.fileId(),
                group.documentType(),
                group.documentNumber(),
                group.title(),
                file.originalName(),
                file.contentType(),
                file.size(),
                downloadUrl,
                "/api/v1/vehicles/" + equipmentId + "/documents/" + group.id() + "/presigned-url",
                group.createdAt(),
                file.uploadedAt(),
                equipmentId,
                group.title(),
                group.documentType(),
                group.createdAt(),
                new FileRef(
                        file.fileId(),
                        file.storedName(),
                        file.originalName(),
                        file.contentType(),
                        file.size(),
                        downloadUrl
                )
        );
    }
}

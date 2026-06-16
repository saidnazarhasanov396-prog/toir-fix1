package com.toir.dto.workorder;

import com.toir.dto.attachment.AttachmentGroupDto;
import com.toir.entity.UploadedFile;
import com.toir.entity.maintenance.WorkOrderDocument;

import java.time.LocalDateTime;
import java.util.UUID;

public record WorkOrderDocumentDto(
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
        UUID workOrderId,
        UUID uploadedById,
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

    public WorkOrderDocumentDto(
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
                presignedUrlEndpoint, createdAt, uploadedAt, null, null, documentName, documentType, createdAt, null);
    }

    public static WorkOrderDocumentDto from(UUID workOrderId, WorkOrderDocument document) {
        if (document == null || document.getFile() == null || Boolean.TRUE.equals(document.getFile().getDeleted())) {
            return null;
        }
        UploadedFile file = document.getFile();
        String downloadUrl = "/api/v1/work-orders/" + workOrderId + "/documents/" + document.getId() + "/download";
        return new WorkOrderDocumentDto(
                document.getId(),
                file.getId(),
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.getDocumentName(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                downloadUrl,
                "/api/v1/work-orders/" + workOrderId + "/documents/" + document.getId() + "/presigned-url",
                document.getCreatedAt(),
                document.getCreatedAt(),
                workOrderId,
                file.getUploadedBy(),
                document.getDocumentName(),
                document.getDocumentType(),
                document.getCreatedAt(),
                new FileRef(
                        file.getId(),
                        file.getStoredName(),
                        file.getOriginalName(),
                        file.getContentType(),
                        file.getSize(),
                        downloadUrl
                )
        );
    }

    public static WorkOrderDocumentDto fromAttachmentGroup(UUID workOrderId, AttachmentGroupDto group) {
        if (group == null || group.files() == null || group.files().isEmpty()) {
            return null;
        }
        AttachmentGroupDto.FileItem file = group.files().getFirst();
        String downloadUrl = "/api/v1/work-orders/" + workOrderId + "/documents/" + group.id() + "/download";
        return new WorkOrderDocumentDto(
                group.id(),
                file.fileId(),
                group.documentType(),
                group.documentNumber(),
                group.title(),
                file.originalName(),
                file.contentType(),
                file.size(),
                downloadUrl,
                "/api/v1/work-orders/" + workOrderId + "/documents/" + group.id() + "/presigned-url",
                group.createdAt(),
                file.uploadedAt(),
                workOrderId,
                file.uploadedBy(),
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

package com.toir.dto.attachment;

import com.toir.entity.UploadedFile;
import com.toir.entity.attachment.AttachmentGroup;
import com.toir.entity.attachment.AttachmentGroupItem;
import com.toir.enums.AttachmentTargetType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record AttachmentGroupDto(
        UUID id,
        String title,
        String description,
        AttachmentTargetType targetType,
        UUID targetId,
        String documentType,
        String documentNumber,
        UUID createdBy,
        LocalDateTime createdAt,
        List<FileItem> files
) {
    public record FileItem(
            UUID itemId,
            UUID fileId,
            String originalName,
            String storedName,
            String contentType,
            Long size,
            Integer orderNumber,
            String label,
            UUID uploadedBy,
            LocalDateTime uploadedAt,
            String downloadUrl,
            String presignedUrlEndpoint
    ) {
    }

    public static AttachmentGroupDto from(AttachmentGroup group) {
        if (group == null) {
            return null;
        }
        List<FileItem> files = group.getItems() == null
                ? List.of()
                : group.getItems().stream()
                .filter(item -> item.getFile() != null && !Boolean.TRUE.equals(item.getFile().getDeleted()))
                .sorted(Comparator.comparing(
                        AttachmentGroupItem::getOrderNumber,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(item -> fileItem(group.getId(), item))
                .toList();
        return new AttachmentGroupDto(
                group.getId(),
                group.getTitle(),
                group.getDescription(),
                group.getTargetType(),
                group.getTargetId(),
                group.getDocumentType(),
                group.getDocumentNumber(),
                group.getCreatedBy(),
                group.getCreatedAt(),
                files
        );
    }

    private static FileItem fileItem(UUID groupId, AttachmentGroupItem item) {
        UploadedFile file = item.getFile();
        String downloadUrl = "/api/v1/attachments/groups/" + groupId + "/files/" + file.getId() + "/download";
        return new FileItem(
                item.getId(),
                file.getId(),
                file.getOriginalName(),
                file.getStoredName(),
                file.getContentType(),
                file.getSize(),
                item.getOrderNumber(),
                item.getLabel(),
                file.getUploadedBy(),
                file.getCreatedAt(),
                downloadUrl,
                "/api/v1/attachments/groups/" + groupId + "/files/" + file.getId() + "/presigned-url"
        );
    }
}

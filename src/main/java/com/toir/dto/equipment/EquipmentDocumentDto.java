package com.toir.dto.equipment;

import com.toir.entity.UploadedFile;
import com.toir.entity.equipment.EquipmentDocument;
import com.toir.entity.equipment.EquipmentDocumentFile;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
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
        LocalDateTime uploadedAt,
        UUID equipmentId,
        String title,
        String type,
        LocalDateTime documentDate,
        FileRef file,
        List<FileRef> files
) {
    public record FileRef(
            UUID id,
            String fileName,
            String originalName,
            String mimeType,
            Long sizeBytes,
            String downloadUrl
    ) {}

    public EquipmentDocumentDto(
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
        this(id, fileId, documentType, documentName, originalName, contentType, size, downloadUrl,
                presignedUrlEndpoint, createdAt, uploadedAt, null, documentName, documentType, createdAt, null, List.of());
    }

    public static EquipmentDocumentDto from(UUID equipmentId, EquipmentDocument document) {
        if (document == null) {
            return null;
        }
        List<FileRef> fileRefs = fileRefs(equipmentId, document);
        if (fileRefs.isEmpty()) {
            return null;
        }
        FileRef primaryFileRef = fileRefs.getFirst();
        UploadedFile file = primaryFile(equipmentId, document, primaryFileRef);
        if (file == null) {
            return null;
        }
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
                document.getCreatedAt(),
                equipmentId,
                document.getDocumentName(),
                document.getDocumentType(),
                document.getCreatedAt(),
                primaryFileRef,
                fileRefs
        );
    }

    private static UploadedFile primaryFile(UUID equipmentId, EquipmentDocument document, FileRef primaryFileRef) {
        if (document.getFile() != null
                && primaryFileRef.id().equals(document.getFile().getId())
                && !Boolean.TRUE.equals(document.getFile().getDeleted())) {
            return document.getFile();
        }
        if (document.getFiles() == null) {
            return null;
        }
        return document.getFiles().stream()
                .map(EquipmentDocumentFile::getFile)
                .filter(file -> file != null && primaryFileRef.id().equals(file.getId()))
                .findFirst()
                .orElse(null);
    }

    private static List<FileRef> fileRefs(UUID equipmentId, EquipmentDocument document) {
        if (document.getFiles() != null && !document.getFiles().isEmpty()) {
            List<FileRef> refs = document.getFiles().stream()
                    .filter(link -> link.getFile() != null && !Boolean.TRUE.equals(link.getFile().getDeleted()))
                    .sorted(Comparator.comparing(
                            EquipmentDocumentFile::getSortOrder,
                            Comparator.nullsLast(Integer::compareTo)
                    ))
                    .map(link -> fileRef(equipmentId, document.getId(), link.getFile()))
                    .toList();
            if (!refs.isEmpty()) {
                return refs;
            }
        }
        if (document.getFile() == null || Boolean.TRUE.equals(document.getFile().getDeleted())) {
            return List.of();
        }
        return List.of(fileRef(equipmentId, document.getId(), document.getFile()));
    }

    private static FileRef fileRef(UUID equipmentId, UUID documentId, UploadedFile file) {
        return new FileRef(
                file.getId(),
                file.getStoredName(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSize(),
                "/api/v1/equipment/" + equipmentId + "/documents/" + documentId + "/files/" + file.getId() + "/download"
        );
    }
}

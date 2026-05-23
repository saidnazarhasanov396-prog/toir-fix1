package com.toir.dto.technicaldocument;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.toir.enums.DocumentType;
import com.toir.entity.TechnicalDocument;
import com.toir.enums.EquipmentNodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record TechnicalDocumentDto(
        UUID id,
        UUID equipmentId,
        UUID equipmentNodeId,
        @JsonAlias("fileAssetId")
        UUID fileId,
        FileRef file,
        @NotBlank String title,
        String revision,
        @JsonAlias("documentType")
        @NotNull DocumentType type,
        LocalDate documentDate,
        UUID uploadedById,
        String equipmentNodeCode,
        String equipmentNodeName,
        EquipmentNodeType equipmentNodeType
) {
    public TechnicalDocumentDto(UUID id,
                                UUID equipmentId,
                                UUID fileId,
                                FileRef file,
                                String title,
                                String revision,
                                DocumentType type,
                                LocalDate documentDate,
                                UUID uploadedById) {
        this(id, equipmentId, null, fileId, file, title, revision, type, documentDate, uploadedById, null, null, null);
    }

    public TechnicalDocumentDto(UUID id,
                                UUID equipmentId,
                                UUID equipmentNodeId,
                                UUID fileId,
                                FileRef file,
                                String title,
                                String revision,
                                DocumentType type,
                                LocalDate documentDate,
                                UUID uploadedById) {
        this(id, equipmentId, equipmentNodeId, fileId, file, title, revision, type, documentDate, uploadedById, null, null, null);
    }

    public record FileRef(
            UUID id,
            String fileName,
            String originalName,
            String mimeType,
            long sizeBytes,
            String downloadUrl
    ) {
    }

    public static TechnicalDocumentDto from(TechnicalDocument d) {
        return from(d, null);
    }

    public static TechnicalDocumentDto from(TechnicalDocument d, FileRef file) {
        return from(d, file, null, null, null);
    }

    public static TechnicalDocumentDto from(TechnicalDocument d,
                                            FileRef file,
                                            String equipmentNodeCode,
                                            String equipmentNodeName,
                                            EquipmentNodeType equipmentNodeType) {
        return new TechnicalDocumentDto(d.getId(), d.getEquipmentId(), d.getEquipmentNodeId(), d.getFileId(), file, d.getTitle(),
                d.getRevision(), d.getType(), d.getDocumentDate(), d.getUploadedById(),
                equipmentNodeCode, equipmentNodeName, equipmentNodeType);
    }
}

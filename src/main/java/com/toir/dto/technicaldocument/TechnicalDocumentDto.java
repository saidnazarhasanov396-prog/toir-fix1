package com.toir.dto.technicaldocument;

import com.toir.enums.DocumentType;
import com.toir.entity.TechnicalDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record TechnicalDocumentDto(
        UUID id,
        UUID equipmentId,
        UUID fileId,
        FileRef file,
        @NotBlank String title,
        String revision,
        @NotNull DocumentType type,
        LocalDate documentDate,
        UUID uploadedById
) {
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
        return new TechnicalDocumentDto(d.getId(), d.getEquipmentId(), d.getFileId(), file, d.getTitle(),
                d.getRevision(), d.getType(), d.getDocumentDate(), d.getUploadedById());
    }
}

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
        @NotBlank String title,
        String revision,
        @NotNull DocumentType type,
        LocalDate documentDate,
        UUID uploadedById
) {
    public static TechnicalDocumentDto from(TechnicalDocument d) {
        return new TechnicalDocumentDto(d.getId(), d.getEquipmentId(), d.getFileId(), d.getTitle(),
                d.getRevision(), d.getType(), d.getDocumentDate(), d.getUploadedById());
    }
}

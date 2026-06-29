package com.toir.dto.wms;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public record WmsDocumentGroupRequest(
        @NotBlank String documentName,
        @NotBlank String documentType,
        String documentNumber,
        LocalDate documentDate,
        UUID attachmentGroupId
) {
    public String normalizedType() {
        return documentType == null ? null : documentType.trim().toUpperCase(Locale.ROOT);
    }
}

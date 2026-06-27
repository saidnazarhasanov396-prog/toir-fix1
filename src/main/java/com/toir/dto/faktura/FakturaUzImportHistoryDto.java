package com.toir.dto.faktura;

import com.toir.entity.faktura.FakturaUzImportHistory;
import com.toir.enums.FakturaUzDocumentType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record FakturaUzImportHistoryDto(
        UUID id,
        UUID endpointId,
        FakturaUzDocumentType documentType,
        LocalDateTime importedAt,
        String description,
        LocalDate importRequestDateFrom,
        LocalDate importRequestDateTo,
        Long totalDataCountInRequest,
        Long totalSavedDataCount,
        Long totalUpdatedDataCount,
        Long totalFailedDataCount
) {
    public static FakturaUzImportHistoryDto from(FakturaUzImportHistory e) {
        return new FakturaUzImportHistoryDto(
                e.getId(),
                e.getEndpointId(),
                e.getDocumentType(),
                e.getImportedAt(),
                e.getDescription(),
                e.getImportRequestDateFrom(),
                e.getImportRequestDateTo(),
                e.getTotalDataCountInRequest(),
                e.getTotalSavedDataCount(),
                e.getTotalUpdatedDataCount(),
                e.getTotalFailedDataCount()
        );
    }
}

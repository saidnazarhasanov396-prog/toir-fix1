package com.toir.dto.faktura;

import com.toir.enums.FakturaUzDocumentType;
import java.util.UUID;

public record FakturaUzImportResponse(
        UUID historyId,
        UUID endpointId,
        FakturaUzDocumentType type,
        long totalCount,
        long savedCount,
        long updatedCount,
        long failedCount,
        String description
) {
}

package com.toir.dto.faktura;

import com.toir.enums.FakturaUzDocumentType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record FakturaUzImportRequest(
        @NotNull UUID endpointId,
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate,
        @NotNull FakturaUzDocumentType type
) {
}

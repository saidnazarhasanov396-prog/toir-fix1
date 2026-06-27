package com.toir.dto.faktura;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record FakturaUzDocumentContentDto(
        String uniqueId,
        Integer type,
        String typeCode,
        String typeName,
        JsonNode rawContent,
        FakturaUzType32ContentDto type32Content,
        List<FakturaUzType32ServiceDto> type32Services,
        List<FakturaUzType32PartDto> type32Parts
) {
}

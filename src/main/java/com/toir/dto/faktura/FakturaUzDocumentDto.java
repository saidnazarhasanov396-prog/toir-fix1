package com.toir.dto.faktura;

import com.toir.entity.faktura.FakturaUzDocument;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record FakturaUzDocumentDto(
        UUID id,
        UUID endpointId,
        String uniqueId,
        String roamingUid,
        Integer type,
        String typeCode,
        String typeName,
        String title,
        String fileName,
        BigDecimal totalPrice,
        String contract,
        Long createdDateTime,
        LocalDateTime createdAtLocal,
        Long updatedDateTime,
        Boolean isNew,
        Integer status,
        String organizationInn,
        String contractorInn,
        String contractorName,
        String ownerInn,
        String ownerName,
        String contractorMemberInn,
        String contractorMemberName
) {
    public static FakturaUzDocumentDto from(FakturaUzDocument e, String typeCode, String typeName) {
        return new FakturaUzDocumentDto(
                e.getId(),
                e.getEndpointId(),
                e.getUniqueId(),
                e.getRoamingUid(),
                e.getType(),
                typeCode,
                typeName,
                e.getTitle(),
                e.getFileName(),
                e.getTotalPrice(),
                e.getContract(),
                e.getCreatedDateTime(),
                e.getCreatedDateTimeAsLocalDateTime(),
                e.getUpdatedDateTime(),
                e.getIsNew(),
                e.getStatus(),
                e.getOrganizationInn(),
                e.getContractorInn(),
                e.getContractorName(),
                e.getOwnerInn(),
                e.getOwnerName(),
                e.getContractorMemberInn(),
                e.getContractorMemberName()
        );
    }
}

package com.toir.dto.faktura;

import com.toir.entity.faktura.FakturaUzDocumentType32Content;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FakturaUzType32ContentDto(
        String documentUniqueId,
        String roamingUid,
        String hasVat,
        String ownerInn,
        String ownerName,
        String clientInn,
        String clientName,
        String contractName,
        String contractorInn,
        String contractNumber,
        LocalDate contractDate,
        LocalDate contractExpireDate,
        String contractPlace,
        BigDecimal invoiceServicesDeliveryCostTotal,
        BigDecimal invoiceServicesVatAmountTotal,
        BigDecimal invoiceServicesTotalPrice,
        String invoiceServicesTotalPriceInWords,
        Boolean isNewIdentity
) {
    public static FakturaUzType32ContentDto from(FakturaUzDocumentType32Content e) {
        if (e == null) {
            return null;
        }
        return new FakturaUzType32ContentDto(
                e.getDocumentUniqueId(),
                e.getRoamingUid(),
                e.getHasVat(),
                e.getOwnerInn(),
                e.getOwnerName(),
                e.getClientInn(),
                e.getClientName(),
                e.getContractName(),
                e.getContractorInn(),
                e.getContractNumber(),
                e.getContractDate(),
                e.getContractExpireDate(),
                e.getContractPlace(),
                e.getInvoiceServicesDeliveryCostTotal(),
                e.getInvoiceServicesVatAmountTotal(),
                e.getInvoiceServicesTotalPrice(),
                e.getInvoiceServicesTotalPriceInWords(),
                e.getIsNewIdentity()
        );
    }
}

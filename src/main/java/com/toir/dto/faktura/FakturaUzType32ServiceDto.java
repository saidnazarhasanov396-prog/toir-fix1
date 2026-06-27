package com.toir.dto.faktura;

import com.toir.entity.faktura.FakturaUzDocumentType32Service;
import java.math.BigDecimal;

public record FakturaUzType32ServiceDto(
        String number,
        String title,
        String measurement,
        String measurementCode,
        Double quantity,
        BigDecimal pricePerItem,
        BigDecimal price,
        BigDecimal deliveryCost,
        Double vatRate,
        BigDecimal vatAmount,
        BigDecimal deliveryCostWithVat,
        BigDecimal deliveryCostWithTaxes,
        String catalogCode,
        String catalogName,
        String barcode
) {
    public static FakturaUzType32ServiceDto from(FakturaUzDocumentType32Service e) {
        return new FakturaUzType32ServiceDto(
                e.getNumber(),
                e.getTitle(),
                e.getMeasurement(),
                e.getMeasurementCode(),
                e.getQuantity(),
                e.getPricePerItem(),
                e.getPrice(),
                e.getDeliveryCost(),
                e.getVatRate(),
                e.getVatAmount(),
                e.getDeliveryCostWithVat(),
                e.getDeliveryCostWithTaxes(),
                e.getCatalogCode(),
                e.getCatalogName(),
                e.getBarcode()
        );
    }
}

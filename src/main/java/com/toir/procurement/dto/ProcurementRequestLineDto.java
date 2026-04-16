package com.toir.procurement.dto;

import com.toir.procurement.ProcurementRequestLine;

import java.util.UUID;

public record ProcurementRequestLineDto(
        UUID id,
        UUID requestId,
        UUID sparePartId,
        double quantity,
        String unit,
        Double unitPrice,
        double estimatedCost,
        String notes
) {
    public static ProcurementRequestLineDto from(ProcurementRequestLine l) {
        return new ProcurementRequestLineDto(
                l.getId(),
                l.getRequest() != null ? l.getRequest().getId() : null,
                l.getSparePartId(),
                l.getQuantity(),
                l.getUnit(),
                l.getUnitPrice(),
                l.getEstimatedCost(),
                l.getNotes()
        );
    }
}

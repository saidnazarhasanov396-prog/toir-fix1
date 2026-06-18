package com.toir.dto.procurement;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.ProcurementRequestLine;

import java.util.UUID;

public record ProcurementRequestLineDto(
        UUID id,
        UUID requestId,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        double quantity,
        double receivedQuantity,
        double remainingQuantity,
        String unit,
        Double unitPrice,
        double estimatedCost,
        String notes
) {
    public static ProcurementRequestLineDto from(ProcurementRequestLine l, SparePart sparePart) {
        return new ProcurementRequestLineDto(
                l.getId(),
                l.getRequest() != null ? l.getRequest().getId() : null,
                l.getSparePartId(),
                sparePart != null ? sparePart.getCode() : null,
                sparePart != null ? sparePart.getName() : null,
                l.getQuantity(),
                l.getReceivedQuantity(),
                l.getRemainingQuantity(),
                l.getUnit(),
                l.getUnitPrice(),
                l.getEstimatedCost(),
                l.getNotes()
        );
    }
}

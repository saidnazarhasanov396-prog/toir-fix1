package com.toir.dto.procurement;

import com.toir.entity.equipment.ProcurementRequestLine;

import java.util.UUID;

public record ProcurementRequestLineDto(
        UUID id,
        UUID requestId,
        UUID sparePartId,
        String sparePartName,
        UUID equipmentTypeId,
        String equipmentTypeName,
        double quantity,
        double receivedQuantity,
        double remainingQuantity,
        String unit,
        Double unitPrice,
        double estimatedCost,
        String notes
) {
    public ProcurementRequestLineDto(UUID id,
                                     UUID requestId,
                                     UUID sparePartId,
                                     double quantity,
                                     double receivedQuantity,
                                     double remainingQuantity,
                                     String unit,
                                     Double unitPrice,
                                     double estimatedCost,
                                     String notes) {
        this(id, requestId, sparePartId, null, null, null, quantity, receivedQuantity,
                remainingQuantity, unit, unitPrice, estimatedCost, notes);
    }

    public static ProcurementRequestLineDto from(ProcurementRequestLine l) {
        return from(l, null);
    }

    public static ProcurementRequestLineDto from(ProcurementRequestLine l, String sparePartName) {
        return new ProcurementRequestLineDto(
                l.getId(),
                l.getRequest() != null ? l.getRequest().getId() : null,
                l.getSparePartId(),
                sparePartName,
                l.getEquipmentTypeId(),
                l.getEquipmentTypeName(),
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

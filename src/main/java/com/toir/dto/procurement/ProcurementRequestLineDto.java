package com.toir.dto.procurement;

import com.toir.entity.SparePart;
import com.toir.entity.equipment.ProcurementRequestLine;

import java.time.LocalDate;
import java.util.UUID;

public record ProcurementRequestLineDto(
        UUID id,
        UUID requestId,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        UUID equipmentTypeId,
        String equipmentTypeName,
        double quantity,
        double receivedQuantity,
        double remainingQuantity,
        String unit,
        Double unitPrice,
        double estimatedCost,
        String notes,
        Boolean hasWarranty,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        Integer warrantyDurationMonths,
        UUID warrantyCounteragentId,
        String warrantyCounteragentName
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
        this(id, requestId, sparePartId, null, null, null, null, quantity, receivedQuantity,
                remainingQuantity, unit, unitPrice, estimatedCost, notes,
                false, null, null, null, null, null);
    }

    public static ProcurementRequestLineDto from(ProcurementRequestLine l) {
        return from(l, null, null, null);
    }

    public static ProcurementRequestLineDto from(ProcurementRequestLine l, SparePart sparePart) {
        return from(l, sparePart, null);
    }

    public static ProcurementRequestLineDto from(ProcurementRequestLine l, SparePart sparePart, String warrantyCounteragentName) {
        return from(
                l,
                sparePart == null ? null : sparePart.getCode(),
                sparePart == null ? null : sparePart.getName(),
                warrantyCounteragentName
        );
    }

    private static ProcurementRequestLineDto from(ProcurementRequestLine l,
                                                  String sparePartCode,
                                                  String sparePartName,
                                                  String warrantyCounteragentName) {
        return new ProcurementRequestLineDto(
                l.getId(),
                l.getRequest() != null ? l.getRequest().getId() : null,
                l.getSparePartId(),
                sparePartCode,
                sparePartName,
                l.getEquipmentTypeId(),
                l.getEquipmentTypeName(),
                l.getQuantity(),
                l.getReceivedQuantity(),
                l.getRemainingQuantity(),
                l.getUnit(),
                l.getUnitPrice(),
                l.getEstimatedCost(),
                l.getNotes(),
                Boolean.TRUE.equals(l.getHasWarranty()),
                l.getWarrantyStartDate(),
                l.getWarrantyEndDate(),
                l.getWarrantyDurationMonths(),
                l.getWarrantyCounteragentId(),
                warrantyCounteragentName
        );
    }
}

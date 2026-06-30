package com.toir.dto.procurement;

import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record ProcurementLineRequest(
        UUID sparePartId,
        @Positive double quantity,
        String unit,
        Double unitPrice,
        String notes,
        UUID equipmentTypeId,
        Boolean hasWarranty,
        LocalDate warrantyStartDate,
        LocalDate warrantyEndDate,
        Integer warrantyDurationMonths,
        UUID warrantyCounteragentId
) {
    public ProcurementLineRequest(UUID sparePartId,
                                  @Positive double quantity,
                                  String unit,
                                  Double unitPrice,
                                  String notes,
                                  UUID equipmentTypeId) {
        this(sparePartId, quantity, unit, unitPrice, notes, equipmentTypeId,
                null, null, null, null, null);
    }

    public ProcurementLineRequest(UUID sparePartId,
                                  @Positive double quantity,
                                  String unit,
                                  Double unitPrice,
                                  String notes) {
        this(sparePartId, quantity, unit, unitPrice, notes, null,
                null, null, null, null, null);
    }
}

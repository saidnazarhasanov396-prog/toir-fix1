package com.toir.dto.procurement;

import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ProcurementLineRequest(
        UUID sparePartId,
        @Positive double quantity,
        String unit,
        Double unitPrice,
        String notes,
        UUID equipmentTypeId
) {
    public ProcurementLineRequest(UUID sparePartId,
                                  @Positive double quantity,
                                  String unit,
                                  Double unitPrice,
                                  String notes) {
        this(sparePartId, quantity, unit, unitPrice, notes, null);
    }
}

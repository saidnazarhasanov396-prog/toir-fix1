package com.toir.dto.equipmentsparepart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record EquipmentSparePartRequest(
        @NotNull UUID sparePartId,
        String position,
        @Positive double quantityPerUnit,
        Double consumptionRatePerYear,
        String criticality,
        String notes
) {}

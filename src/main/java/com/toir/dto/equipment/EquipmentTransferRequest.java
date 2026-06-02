package com.toir.dto.equipment;

import jakarta.validation.constraints.NotNull;

public record EquipmentTransferRequest(
        @NotNull EquipmentLocationRequest targetLocation,
        String note
) {
}

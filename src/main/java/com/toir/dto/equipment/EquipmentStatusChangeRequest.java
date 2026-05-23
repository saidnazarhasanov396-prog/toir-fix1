package com.toir.dto.equipment;

import com.toir.enums.EquipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EquipmentStatusChangeRequest(
        @NotNull EquipmentStatus status,
        @NotBlank String reason
) {
}

package com.toir.dto.workorder;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CompleteWorkOrderRequest(
        @NotBlank String result,
        String summary,
        UUID oldEquipmentReturnWarehouseId
) {}

package com.toir.dto.warehouse;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record WarehouseRequest(
        String code,
        @NotBlank String name,
        UUID departmentId,
        UUID locationId,
        UUID responsibleId,
        Boolean active
) {}

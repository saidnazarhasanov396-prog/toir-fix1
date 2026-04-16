package com.toir.warehouse.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record WarehouseRequest(
        @NotBlank String code,
        @NotBlank String name,
        UUID departmentId,
        UUID locationId,
        UUID responsibleId,
        Boolean active
) {}

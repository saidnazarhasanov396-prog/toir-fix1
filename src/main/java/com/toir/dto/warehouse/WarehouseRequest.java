package com.toir.dto.warehouse;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record WarehouseRequest(
        String code,
        @NotBlank String name,
        @JsonAlias("department_id")
        UUID departmentId,
        @JsonAlias("location_id")
        UUID locationId,
        @JsonAlias("responsible_id")
        UUID responsibleId,
        Boolean active
) {}

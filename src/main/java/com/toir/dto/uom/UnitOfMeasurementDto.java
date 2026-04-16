package com.toir.dto.uom;

import com.toir.entity.UnitOfMeasurement;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UnitOfMeasurementDto(
        UUID id,
        @NotBlank String code,
        @NotBlank String name
) {
    public static UnitOfMeasurementDto from(UnitOfMeasurement u) {
        return new UnitOfMeasurementDto(u.getId(), u.getCode(), u.getName());
    }
}

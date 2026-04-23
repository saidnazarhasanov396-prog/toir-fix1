package com.toir.dto.meter;

import com.toir.entity.MeterType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record EquipmentMeterRequest(
        @NotNull UUID equipmentId,
        @NotNull MeterType meterType,
        @NotBlank String name,
        @NotBlank String unit,
        @PositiveOrZero double initialValue,
        Double rolloverValue,
        Boolean active
) {}

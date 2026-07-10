package com.toir.dto.meter;

import com.toir.enums.MeterType;
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
        Boolean active,
        Boolean primary
) {
    public EquipmentMeterRequest(UUID equipmentId,
                                 MeterType meterType,
                                 String name,
                                 String unit,
                                 double initialValue,
                                 Double rolloverValue,
                                 Boolean active) {
        this(equipmentId, meterType, name, unit, initialValue, rolloverValue, active, false);
    }
}

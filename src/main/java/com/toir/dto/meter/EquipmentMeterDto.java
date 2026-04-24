package com.toir.dto.meter;

import com.toir.entity.EquipmentMeter;
import com.toir.enums.MeterType;

import java.time.Instant;
import java.util.UUID;

public record EquipmentMeterDto(
        UUID id,
        UUID equipmentId,
        MeterType meterType,
        String name,
        String unit,
        double currentValue,
        Instant lastReadAt,
        Double rolloverValue,
        boolean active
) {
    public static EquipmentMeterDto from(EquipmentMeter m) {
        return new EquipmentMeterDto(
                m.getId(), m.getEquipmentId(), m.getMeterType(), m.getName(), m.getUnit(),
                m.getCurrentValue(), m.getLastReadAt(), m.getRolloverValue(), m.isActive()
        );
    }
}

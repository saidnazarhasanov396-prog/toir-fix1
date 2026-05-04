package com.toir.dto.meter;

import com.toir.entity.equipment.EquipmentMeter;
import com.toir.enums.MeterType;

import java.time.Instant;
import java.util.UUID;

public record EquipmentMeterDto(
        UUID id,
        UUID equipmentId,
        String equipmentName,
        MeterType meterType,
        String name,
        String unit,
        double currentValue,
        Instant lastReadAt,
        Double rolloverValue,
        boolean active
) {
    public static EquipmentMeterDto from(EquipmentMeter m,String equipmentName) {
        return new EquipmentMeterDto(
                m.getId(), m.getEquipmentId(),equipmentName, m.getMeterType(), m.getName(), m.getUnit(),
                m.getCurrentValue(), m.getLastReadAt(), m.getRolloverValue(), m.isActive()
        );
    }
}

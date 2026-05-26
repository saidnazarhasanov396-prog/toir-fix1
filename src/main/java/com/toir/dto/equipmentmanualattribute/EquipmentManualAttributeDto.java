package com.toir.dto.equipmentmanualattribute;

import com.toir.entity.equipment.EquipmentManualAttribute;

import java.util.UUID;

public record EquipmentManualAttributeDto(
        UUID id,
        String key,
        String value
) {
    public static EquipmentManualAttributeDto from(EquipmentManualAttribute attribute) {
        return new EquipmentManualAttributeDto(
                attribute.getId(),
                attribute.getKey(),
                attribute.getValue()
        );
    }
}

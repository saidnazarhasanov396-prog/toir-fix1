package com.toir.dto.equipmentattribute;

import com.toir.entity.equipment.EquipmentAttributeOptionSource;

import java.util.UUID;

public record EquipmentAttributeOptionSourceDto(
        UUID id,
        String code,
        String name,
        String nameRu,
        String nameUz,
        String description
) {
    public static EquipmentAttributeOptionSourceDto from(EquipmentAttributeOptionSource source) {
        return new EquipmentAttributeOptionSourceDto(
                source.getId(),
                source.getCode(),
                source.getName(),
                source.getNameRu(),
                source.getNameUz(),
                source.getDescription()
        );
    }
}

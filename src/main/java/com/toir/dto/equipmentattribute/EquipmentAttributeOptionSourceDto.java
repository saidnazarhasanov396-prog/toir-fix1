package com.toir.dto.equipmentattribute;

import com.toir.entity.equipment.EquipmentAttributeOptionSource;

import java.util.UUID;

public record EquipmentAttributeOptionSourceDto(
        UUID id,
        String code,
        String name,
        String nameRu,
        String nameUz,
        String description,
        long optionCounts
) {
    public static EquipmentAttributeOptionSourceDto from(EquipmentAttributeOptionSource source) {
        return from(source, 0);
    }

    public static EquipmentAttributeOptionSourceDto from(EquipmentAttributeOptionSource source, long optionCounts) {
        return new EquipmentAttributeOptionSourceDto(
                source.getId(),
                source.getCode(),
                source.getName(),
                source.getNameRu(),
                source.getNameUz(),
                source.getDescription(),
                optionCounts
        );
    }
}

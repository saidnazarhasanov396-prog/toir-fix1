package com.toir.equipmenttype.dto;

import com.toir.equipmenttype.EquipmentType;

import java.util.UUID;

public record EquipmentTypeDto(
        UUID id,
        String code,
        String name,
        String nameEn,
        String nameUz,
        String category,
        String description
) {
    public static EquipmentTypeDto from(EquipmentType t) {
        return new EquipmentTypeDto(t.getId(), t.getCode(), t.getName(), t.getNameEn(), t.getNameUz(),
                t.getCategory(), t.getDescription());
    }
}

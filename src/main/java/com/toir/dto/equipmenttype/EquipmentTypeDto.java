package com.toir.dto.equipmenttype;

import com.toir.entity.EquipmentType;

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

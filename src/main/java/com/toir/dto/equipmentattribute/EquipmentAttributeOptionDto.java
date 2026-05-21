package com.toir.dto.equipmentattribute;

public record EquipmentAttributeOptionDto(
        String id,
        String label,
        String labelRu,
        String labelUz,
        Integer sortOrder,
        Boolean active
) {}

package com.toir.dto.equipmentattribute;

import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.enums.EquipmentAttributeDataType;

import java.util.List;
import java.util.UUID;

public record EquipmentAttributeDefinitionDto(
        UUID id,
        UUID equipmentTypeId,
        String key,
        String label,
        String labelRu,
        String labelUz,
        EquipmentAttributeDataType dataType,
        String unit,
        boolean required,
        Double minValue,
        Double maxValue,
        List<String> options,
        String groupName,
        Integer sortOrder
) {
    public static EquipmentAttributeDefinitionDto from(EquipmentAttributeDefinition definition) {
        return new EquipmentAttributeDefinitionDto(
                definition.getId(),
                definition.getEquipmentTypeId(),
                definition.getKey(),
                definition.getLabel(),
                definition.getLabelRu(),
                definition.getLabelUz(),
                definition.getDataType(),
                definition.getUnit(),
                definition.isRequired(),
                definition.getMinValue(),
                definition.getMaxValue(),
                definition.getOptions() == null ? List.of() : definition.getOptions(),
                definition.getGroupName(),
                definition.getSortOrder()
        );
    }
}

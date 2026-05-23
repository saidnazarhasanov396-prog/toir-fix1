package com.toir.dto.equipmentattribute;

import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.dto.uom.UnitOfMeasurementDto;

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
        UnitOfMeasurementDto unit,
        boolean required,
        Double minValue,
        Double maxValue,
        UUID optionSourceId,
        List<EquipmentAttributeOptionDto> options,
        String groupName,
        Integer sortOrder
) {
    public static EquipmentAttributeDefinitionDto from(EquipmentAttributeDefinition definition) {
        return from(definition, null);
    }

    public static EquipmentAttributeDefinitionDto from(EquipmentAttributeDefinition definition, UnitOfMeasurementDto unit) {
        return new EquipmentAttributeDefinitionDto(
                definition.getId(),
                definition.getEquipmentTypeId(),
                definition.getKey(),
                definition.getLabel(),
                definition.getLabelRu(),
                definition.getLabelUz(),
                definition.getDataType(),
                unit,
                definition.isRequired(),
                definition.getMinValue(),
                definition.getMaxValue(),
                definition.getOptionSourceId(),
                definition.getOptions() == null ? List.of() : definition.getOptions(),
                definition.getGroupName(),
                definition.getSortOrder()
        );
    }
}

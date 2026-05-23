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
        Integer sortOrder,
        List<UUID> requiredForCriticalityClassIds
) {
    public EquipmentAttributeDefinitionDto(
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
        this(id, equipmentTypeId, key, label, labelRu, labelUz, dataType, unit, required, minValue, maxValue,
                optionSourceId, options, groupName, sortOrder, List.of());
    }

    public static EquipmentAttributeDefinitionDto from(EquipmentAttributeDefinition definition) {
        return from(definition, null, List.of());
    }

    public static EquipmentAttributeDefinitionDto from(EquipmentAttributeDefinition definition,
                                                       List<UUID> requiredForCriticalityClassIds) {
        return from(definition, null, requiredForCriticalityClassIds);
    }

    public static EquipmentAttributeDefinitionDto from(EquipmentAttributeDefinition definition,
                                                       UnitOfMeasurementDto unit,
                                                       List<UUID> requiredForCriticalityClassIds) {
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
                definition.getSortOrder(),
                requiredForCriticalityClassIds == null ? List.of() : requiredForCriticalityClassIds
        );
    }
}

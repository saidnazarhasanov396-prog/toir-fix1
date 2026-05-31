package com.toir.dto.equipmentattribute;

import com.toir.dto.uom.UnitOfMeasurementDto;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.enums.EquipmentAttributeDataType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EquipmentAttributeValueDto(
        UUID id,
        UUID equipmentId,
        UUID attributeDefinitionId,
        String key,
        String label,
        String labelRu,
        String labelUz,
        EquipmentAttributeDataType dataType,
        UnitOfMeasurementDto unit,
        boolean required,
        UUID optionSourceId,
        List<EquipmentAttributeOptionDto> options,
        String groupName,
        Integer sortOrder,
        String valueText,
        Double valueNumber,
        LocalDate valueDate,
        Boolean valueBoolean,
        String valueOption,
        String valueJson
) {
    public EquipmentAttributeValueDto(
            UUID id,
            UUID equipmentId,
            UUID attributeDefinitionId,
            String key,
            String label,
            String labelRu,
            String labelUz,
            EquipmentAttributeDataType dataType,
            String unit,
            boolean required,
            UUID optionSourceId,
            List<EquipmentAttributeOptionDto> options,
            String groupName,
            Integer sortOrder,
            String valueText,
            Double valueNumber,
            LocalDate valueDate,
            Boolean valueBoolean,
            String valueOption,
            String valueJson
    ) {
        this(
                id,
                equipmentId,
                attributeDefinitionId,
                key,
                label,
                labelRu,
                labelUz,
                dataType,
                unit == null ? null : new UnitOfMeasurementDto(null, null, unit),
                required,
                optionSourceId,
                options,
                groupName,
                sortOrder,
                valueText,
                valueNumber,
                valueDate,
                valueBoolean,
                valueOption,
                valueJson
        );
    }

    public static EquipmentAttributeValueDto from(EquipmentAttributeDefinition definition,
                                                  EquipmentAttributeValue value) {
        return from(definition, value, value == null ? null : value.getEquipmentId());
    }

    public static EquipmentAttributeValueDto from(EquipmentAttributeDefinition definition,
                                                  EquipmentAttributeValue value,
                                                  UUID equipmentId) {
        return from(definition, value, equipmentId, null);
    }

    public static EquipmentAttributeValueDto from(EquipmentAttributeDefinition definition,
                                                  EquipmentAttributeValue value,
                                                  UUID equipmentId,
                                                  UnitOfMeasurementDto unit) {
        return new EquipmentAttributeValueDto(
                value == null ? null : value.getId(),
                equipmentId,
                definition.getId(),
                definition.getKey(),
                definition.getLabel(),
                definition.getLabelRu(),
                definition.getLabelUz(),
                definition.getDataType(),
                unit,
                definition.isRequired(),
                definition.getOptionSourceId(),
                definition.getOptions() == null ? List.of() : definition.getOptions(),
                definition.getGroupName(),
                definition.getSortOrder(),
                value == null ? null : value.getValueText(),
                value == null ? null : value.getValueNumber(),
                value == null ? null : value.getValueDate(),
                value == null ? null : value.getValueBoolean(),
                value == null ? null : value.getValueOption(),
                value == null ? null : value.getValueJson()
        );
    }
}

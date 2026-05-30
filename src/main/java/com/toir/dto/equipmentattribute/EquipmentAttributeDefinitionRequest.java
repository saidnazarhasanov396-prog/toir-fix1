package com.toir.dto.equipmentattribute;

import com.toir.enums.EquipmentAttributeDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record EquipmentAttributeDefinitionRequest(
        @NotBlank String key,
        @NotBlank String label,
        String labelRu,
        String labelUz,
        @NotNull EquipmentAttributeDataType dataType,
        String unit,
        boolean required,
        Double minValue,
        Double maxValue,
        UUID optionSourceId,
        List<EquipmentAttributeOptionDto> options,
        String groupName,
        Integer sortOrder,
        List<UUID> requiredForCriticalityClassIds,
        UUID unitId
) {
    public EquipmentAttributeDefinitionRequest(
            @NotBlank String key,
            @NotBlank String label,
            String labelRu,
            String labelUz,
            @NotNull EquipmentAttributeDataType dataType,
            String unit,
            boolean required,
            Double minValue,
            Double maxValue,
            UUID optionSourceId,
            List<EquipmentAttributeOptionDto> options,
            String groupName,
            Integer sortOrder
    ) {
        this(key, label, labelRu, labelUz, dataType, unit, required, minValue, maxValue,
                optionSourceId, options, groupName, sortOrder, List.of(), null);
    }

    public EquipmentAttributeDefinitionRequest(
            @NotBlank String key,
            @NotBlank String label,
            String labelRu,
            String labelUz,
            @NotNull EquipmentAttributeDataType dataType,
            String unit,
            boolean required,
            Double minValue,
            Double maxValue,
            UUID optionSourceId,
            List<EquipmentAttributeOptionDto> options,
            String groupName,
            Integer sortOrder,
            List<UUID> requiredForCriticalityClassIds
    ) {
        this(key, label, labelRu, labelUz, dataType, unit, required, minValue, maxValue,
                optionSourceId, options, groupName, sortOrder, requiredForCriticalityClassIds, null);
    }

    public List<UUID> normalizedRequiredForCriticalityClassIds() {
        return requiredForCriticalityClassIds == null ? List.of() : requiredForCriticalityClassIds;
    }
}

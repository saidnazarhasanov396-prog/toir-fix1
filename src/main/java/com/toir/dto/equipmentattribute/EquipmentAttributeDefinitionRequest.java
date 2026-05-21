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
        Integer sortOrder
) {}

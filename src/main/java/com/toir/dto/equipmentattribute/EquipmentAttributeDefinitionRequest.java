package com.toir.dto.equipmentattribute;

import com.toir.enums.EquipmentAttributeDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

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
        List<String> options,
        String groupName,
        Integer sortOrder
) {}

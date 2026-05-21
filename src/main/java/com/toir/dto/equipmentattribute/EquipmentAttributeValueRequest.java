package com.toir.dto.equipmentattribute;

import java.time.LocalDate;
import java.util.UUID;

public record EquipmentAttributeValueRequest(
        UUID attributeDefinitionId,
        String key,
        String valueText,
        Double valueNumber,
        LocalDate valueDate,
        Boolean valueBoolean,
        String valueOption,
        String valueJson
) {}

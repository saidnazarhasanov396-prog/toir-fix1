package com.toir.dto.equipmentmanualattribute;

import jakarta.validation.Valid;

import java.util.List;

public record BulkEquipmentManualAttributeRequest(
        List<@Valid EquipmentManualAttributeRequest> attributes
) {
}

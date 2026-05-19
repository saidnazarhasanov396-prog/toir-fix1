package com.toir.dto.equipmentlabel;

import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;

import java.util.List;
import java.util.UUID;

public record EquipmentLabelResponse(
        EquipmentRef equipment,
        UUID equipmentId,
        String code,
        String inventoryNumber,
        String name,
        String qrPayload,
        String issuedAt,
        List<EquipmentRef> matches
) {
    public EquipmentLabelResponse {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }

    public record EquipmentRef(
            UUID id,
            String code,
            String name,
            String inventoryNumber,
            String technicalNumber,
            String serialNumber,
            String model,
            UUID equipmentTypeId,
            UUID departmentId,
            UUID locationId,
            UUID parentId,
            String manufacturer,
            EquipmentStatus status,
            EquipmentCategory category
    ) {
    }
}

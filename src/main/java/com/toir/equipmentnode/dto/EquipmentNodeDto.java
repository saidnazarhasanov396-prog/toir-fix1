package com.toir.equipmentnode.dto;

import com.toir.equipmentnode.EquipmentNode;
import com.toir.equipmentnode.EquipmentNodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EquipmentNodeDto(
        UUID id,
        UUID equipmentId,
        UUID parentId,
        @NotBlank String code,
        @NotBlank String name,
        @NotNull EquipmentNodeType nodeType,
        String serialNumber,
        String description
) {
    public static EquipmentNodeDto from(EquipmentNode n) {
        return new EquipmentNodeDto(n.getId(), n.getEquipmentId(), n.getParentId(),
                n.getCode(), n.getName(), n.getNodeType(), n.getSerialNumber(), n.getDescription());
    }
}

package com.toir.dto.equipmentnode;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.enums.EquipmentNodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EquipmentNodeDto(
        UUID id,
        UUID equipmentId,
        @JsonAlias("parentNodeId")
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

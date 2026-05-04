package com.toir.dto.equipmentlabel;

import com.toir.entity.equipment.Equipment;

import java.util.UUID;

public record EquipmentLabelResponse(
        Equipment equipment,
        UUID equipmentId,
        String code,
        String inventoryNumber,
        String name,
        String qrPayload,
        String issuedAt
) {
}

package com.toir.dto.equipmentsparepart;

import com.toir.entity.equipment.EquipmentSparePart;

import java.util.UUID;

public record EquipmentSparePartDto(
        UUID id,
        UUID equipmentId,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        String unit,
        String position,
        double quantityPerUnit,
        Double consumptionRatePerYear,
        String criticality,
        String notes
) {
    public static EquipmentSparePartDto from(EquipmentSparePart e) {
        return new EquipmentSparePartDto(
                e.getId(), e.getEquipmentId(), e.getSparePartId(),
                null, null, null,
                e.getPosition(), e.getQuantityPerUnit(), e.getConsumptionRatePerYear(),
                e.getCriticality(), e.getNotes()
        );
    }

    public static EquipmentSparePartDto from(EquipmentSparePart e, String code, String name, String unit) {
        return new EquipmentSparePartDto(
                e.getId(), e.getEquipmentId(), e.getSparePartId(),
                code, name, unit,
                e.getPosition(), e.getQuantityPerUnit(), e.getConsumptionRatePerYear(),
                e.getCriticality(), e.getNotes()
        );
    }
}

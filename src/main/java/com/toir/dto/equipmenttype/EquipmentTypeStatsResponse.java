package com.toir.dto.equipmenttype;

public record EquipmentTypeStatsResponse(
        long totalTypes,
        long activeCategories,
        long withActiveEquipment,
        long recentlyAdded
) {
}

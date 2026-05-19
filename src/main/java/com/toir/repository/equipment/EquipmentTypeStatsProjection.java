package com.toir.repository.equipment;

public interface EquipmentTypeStatsProjection {
    Long getTotalTypes();
    Long getActiveCategories();
    Long getWithActiveEquipment();
    Long getRecentlyAdded();
}

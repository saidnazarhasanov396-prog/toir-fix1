package com.toir.dto.maintenanceregulation;

import com.toir.enums.MaintenanceKind;
import java.util.UUID;

public record MaintenanceRegulationFilter(
        String search,
        MaintenanceKind maintenanceType,
        UUID equipmentTypeId,
        Boolean active
) {
    public MaintenanceRegulationFilter {
        search = normalizeSearch(search);
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

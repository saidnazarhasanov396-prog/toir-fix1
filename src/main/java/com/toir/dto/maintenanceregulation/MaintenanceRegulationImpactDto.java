package com.toir.dto.maintenanceregulation;

import com.toir.enums.DuplicatePolicy;
import java.util.List;
import java.util.UUID;

public record MaintenanceRegulationImpactDto(
        UUID regulationId,
        long affectedEquipment,
        long matchedCount,
        long unmatchedCount,
        long blockedCount,
        long missingMetersCount,
        String automationSummary,
        DuplicatePolicy duplicatePolicy,
        List<MaintenanceRegulationPreviewDto.Item> items
) {
}

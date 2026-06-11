package com.toir.dto.dashboard;

import com.toir.enums.EquipmentLifecycleStage;

import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/dashboards/equipment-lifecycle
 */
public record EquipmentLifecycleSummaryResponse(

        /** Har bir bosqichda nechta uskuna */
        int lowRiskCount,
        int mediumRiskCount,
        int highRiskCount,
        int inRepairCount,
        int decommissionedCount,
        int totalCount,

        /** Diqqat talab qiladigan uskunalar (HIGH_RISK) */
        List<EquipmentLifecycleItem> highRiskEquipments

) {
    public record EquipmentLifecycleItem(
            UUID                    equipmentId,
            String                  equipmentCode,
            String                  equipmentName,
            EquipmentLifecycleStage stage,
            int                     riskScore,
            long                    openDefects
    ) {}
}

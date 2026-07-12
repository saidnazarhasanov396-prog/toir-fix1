package com.toir.dto.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownAsset;
import com.toir.enums.PlannedShutdownAssetDisposition;

import java.util.UUID;

public record PlannedShutdownAssetResponse(
        UUID id,
        UUID equipmentId,
        PlannedShutdownAssetDisposition disposition,
        String inclusionReason,
        int orderNumber
) {
    public static PlannedShutdownAssetResponse from(PlannedShutdownAsset asset) {
        return new PlannedShutdownAssetResponse(asset.getId(), asset.getEquipmentId(), asset.getDisposition(),
                asset.getInclusionReason(), asset.getOrderNumber());
    }
}

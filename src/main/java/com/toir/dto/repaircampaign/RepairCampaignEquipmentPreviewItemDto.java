package com.toir.dto.repaircampaign;

import java.util.UUID;

public record RepairCampaignEquipmentPreviewItemDto(
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        UUID equipmentTypeId,
        UUID departmentId
) {
}

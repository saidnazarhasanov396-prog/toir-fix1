package com.toir.dto.repaircampaign;

import com.toir.enums.RepairCampaignWorkItemSourceType;
import com.toir.enums.RepairCampaignWorkItemStatus;

import java.util.UUID;

public record RepairCampaignWorkItemResponse(
        UUID id,
        UUID campaignId,
        RepairCampaignWorkItemSourceType sourceType,
        UUID sourceId,
        UUID equipmentId,
        String title,
        RepairCampaignWorkItemStatus status,
        Integer orderNumber,
        String notes,
        Long campaignVersion,
        com.toir.enums.RepairCampaignPriority priority
) {
    public RepairCampaignWorkItemResponse(UUID id, UUID campaignId, RepairCampaignWorkItemSourceType sourceType,
            UUID sourceId, UUID equipmentId, String title, RepairCampaignWorkItemStatus status,
            Integer orderNumber, String notes, Long campaignVersion) {
        this(id, campaignId, sourceType, sourceId, equipmentId, title, status, orderNumber, notes,
                campaignVersion, com.toir.enums.RepairCampaignPriority.MEDIUM);
    }
}

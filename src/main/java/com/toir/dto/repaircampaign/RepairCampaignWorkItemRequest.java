package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.toir.enums.RepairCampaignWorkItemSourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@JsonIgnoreProperties(value = "status")
public record RepairCampaignWorkItemRequest(
        @NotNull Long version,
        @NotNull RepairCampaignWorkItemSourceType sourceType,
        UUID sourceId,
        @NotNull UUID equipmentId,
        @Size(max = 500) String title,
        @NotNull @Min(0) Integer orderNumber,
        String notes,
        com.toir.enums.RepairCampaignPriority priority
) {
    public RepairCampaignWorkItemRequest {
        priority = priority == null ? com.toir.enums.RepairCampaignPriority.MEDIUM : priority;
    }
    public RepairCampaignWorkItemRequest(Long version, RepairCampaignWorkItemSourceType sourceType, UUID sourceId,
            UUID equipmentId, String title, Integer orderNumber, String notes) {
        this(version, sourceType, sourceId, equipmentId, title, orderNumber, notes,
                com.toir.enums.RepairCampaignPriority.MEDIUM);
    }
}

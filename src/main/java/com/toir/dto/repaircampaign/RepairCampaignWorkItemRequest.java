package com.toir.dto.repaircampaign;

import com.toir.enums.RepairCampaignWorkItemSourceType;
import com.toir.enums.RepairCampaignWorkItemStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RepairCampaignWorkItemRequest(
        @NotNull Long version,
        @NotNull RepairCampaignWorkItemSourceType sourceType,
        UUID sourceId,
        @NotNull UUID equipmentId,
        @Size(max = 500) String title,
        RepairCampaignWorkItemStatus status,
        @NotNull @Min(0) Integer orderNumber,
        String notes
) { }

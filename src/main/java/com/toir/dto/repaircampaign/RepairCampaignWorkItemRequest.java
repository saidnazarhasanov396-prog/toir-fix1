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
        String notes
) { }

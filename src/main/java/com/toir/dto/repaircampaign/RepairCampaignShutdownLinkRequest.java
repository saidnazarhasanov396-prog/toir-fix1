package com.toir.dto.repaircampaign;

import jakarta.validation.constraints.NotNull;

public record RepairCampaignShutdownLinkRequest(
        @NotNull Long repairCampaignVersion,
        @NotNull Long plannedShutdownVersion) { }

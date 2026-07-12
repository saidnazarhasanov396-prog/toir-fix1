package com.toir.dto.repaircampaign;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RepairCampaignWorkItemWindowRequest(
        @NotNull Long repairCampaignVersion,
        @NotNull Long plannedShutdownVersion,
        @NotNull UUID repairCampaignWorkItemId,
        @NotNull UUID shutdownWorkItemId) { }

package com.toir.dto.repaircampaign;

import java.util.UUID;

public record RepairCampaignWorkItemWindowResponse(
        UUID id, UUID repairCampaignId, UUID repairCampaignWorkItemId,
        UUID plannedShutdownId, UUID shutdownWorkItemId,
        Long repairCampaignVersion, Long plannedShutdownVersion, boolean active) { }

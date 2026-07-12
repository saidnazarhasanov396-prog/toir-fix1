package com.toir.dto.repaircampaign;

import java.util.UUID;

public record RepairCampaignShutdownLinkResponse(
        UUID id, UUID repairCampaignId, UUID plannedShutdownId,
        Long repairCampaignVersion, Long plannedShutdownVersion, boolean active) { }

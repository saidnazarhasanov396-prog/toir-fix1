package com.toir.dto.repaircampaign;
import java.util.UUID;
public record RepairCampaignDependencyResponse(UUID id,UUID campaignId,UUID predecessorId,UUID successorId,Long campaignVersion,boolean active) {}

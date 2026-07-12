package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.util.UUID;

public record RepairCampaignMaterialRequirementResponse(
        UUID id,UUID campaignId,UUID workItemId,UUID sparePartId,UUID warehouseId,
        @JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal requiredQuantity,
        boolean critical,boolean procurementRequired,long campaignVersion,boolean active) {}

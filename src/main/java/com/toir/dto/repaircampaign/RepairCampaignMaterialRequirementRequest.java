package com.toir.dto.repaircampaign;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RepairCampaignMaterialRequirementRequest(
        @NotNull Long version,
        @NotNull UUID workItemId,
        @NotNull UUID sparePartId,
        @NotNull UUID warehouseId,
        @NotNull @DecimalMin(value="0",inclusive=false)
        @JsonDeserialize(using=com.toir.dto.common.MoneyDecimalStringDeserializer.class) BigDecimal requiredQuantity,
        boolean critical,
        boolean procurementRequired) {}

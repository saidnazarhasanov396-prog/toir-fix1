package com.toir.dto.materialusage;

import com.toir.entity.RepairMaterialUsage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record RepairMaterialUsageDto(
        UUID id,
        UUID workOrderId,
        @NotNull UUID warehouseId,
        @NotNull UUID sparePartId,
        @Positive double quantity,
        Double unitCost
) {
    public static RepairMaterialUsageDto from(RepairMaterialUsage u) {
        return new RepairMaterialUsageDto(u.getId(), u.getWorkOrderId(), u.getWarehouseId(),
                u.getSparePartId(), u.getQuantity(), u.getUnitCost());
    }
}

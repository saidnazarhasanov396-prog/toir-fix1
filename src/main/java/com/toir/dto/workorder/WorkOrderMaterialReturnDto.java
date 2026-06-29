package com.toir.dto.workorder;

import com.toir.entity.warehouse.RepairMaterialReturn;
import com.toir.enums.RepairMaterialReturnStatus;
import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WorkOrderMaterialReturnDto(
        UUID id,
        UUID workOrderId,
        UUID materialUsageId,
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        BigDecimal quantity,
        String reason,
        UUID returnedById,
        UUID responsiblePersonId,
        UUID stockMovementId,
        UUID inventoryTransactionId,
        RepairMaterialReturnStatus status
) {
    public WorkOrderMaterialReturnDto(
            UUID id,
            UUID workOrderId,
            UUID materialUsageId,
            UUID warehouseId,
            UUID sparePartId,
            BigDecimal quantity,
            String status
    ) {
        this(id, workOrderId, materialUsageId, warehouseId, sparePartId, null, null, null, null,
                null, quantity, null, null, null, null, null,
                status == null ? null : RepairMaterialReturnStatus.valueOf(status));
    }

    public static WorkOrderMaterialReturnDto from(RepairMaterialReturn materialReturn) {
        return new WorkOrderMaterialReturnDto(
                materialReturn.getId(),
                materialReturn.getWorkOrderId(),
                materialReturn.getMaterialUsageId(),
                materialReturn.getWarehouseId(),
                materialReturn.getSparePartId(),
                materialReturn.getBinId(),
                materialReturn.getLotNumber(),
                materialReturn.getSerialNumber(),
                materialReturn.getExpiryDate(),
                materialReturn.getStockStatus(),
                materialReturn.getQuantity(),
                materialReturn.getReason(),
                materialReturn.getReturnedById(),
                materialReturn.getResponsiblePersonId(),
                materialReturn.getStockMovementId(),
                materialReturn.getInventoryTransactionId(),
                materialReturn.getStatus()
        );
    }
}

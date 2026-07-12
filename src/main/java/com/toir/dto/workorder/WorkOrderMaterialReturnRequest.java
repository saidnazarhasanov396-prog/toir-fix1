package com.toir.dto.workorder;

import com.toir.dto.wms.WmsDocumentGroupRequest;
import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkOrderMaterialReturnRequest(
        UUID materialUsageId,
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        @jakarta.validation.constraints.Positive @jakarta.validation.constraints.Digits(integer=15,fraction=4)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.toir.dto.common.MoneyDecimalStringDeserializer.class) BigDecimal quantity,
        String reason,
        UUID returnedById,
        UUID responsiblePersonId,
        String documentNumber,
        List<WmsDocumentGroupRequest> documentGroups,
        Boolean strictDocumentPolicy
) {
    public WorkOrderMaterialReturnRequest(
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
            UUID responsiblePersonId
    ) {
        this(materialUsageId, warehouseId, sparePartId, binId, lotNumber, serialNumber, expiryDate, stockStatus,
                quantity, reason, returnedById, responsiblePersonId, null, List.of(), false);
    }

    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }

    public boolean strictPolicyEnabled() {
        return Boolean.TRUE.equals(strictDocumentPolicy);
    }
}

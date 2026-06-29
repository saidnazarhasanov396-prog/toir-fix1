package com.toir.dto.wms;

import com.toir.enums.WarehouseStockStatus;

import java.time.LocalDate;
import java.util.UUID;

public record WmsStockIdentityRequest(
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public WarehouseStockStatus effectiveStatus() {
        return stockStatus == null ? WarehouseStockStatus.AVAILABLE : stockStatus;
    }
}

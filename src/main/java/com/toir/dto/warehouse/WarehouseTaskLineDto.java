package com.toir.dto.warehouse;

import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseTaskLineStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WarehouseTaskLineDto(
        UUID id,
        UUID sparePartId,
        UUID equipmentId,
        UUID fromBinId,
        UUID toBinId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        BigDecimal plannedQty,
        BigDecimal actualQty,
        String unit,
        WarehouseTaskLineStatus status,
        boolean scanConfirmed,
        String exceptionReason,
        String fromBinCode,
        String toBinCode,
        String sparePartCode,
        String sparePartName,
        String equipmentCode,
        String equipmentName
) {}

package com.toir.dto.warehouse;

import com.toir.entity.warehouse.WarehouseTaskLine;
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
) {
    public static WarehouseTaskLineDto from(WarehouseTaskLine line) {
        return new WarehouseTaskLineDto(
                line.getId(),
                line.getSparePartId(),
                line.getEquipmentId(),
                line.getFromBinId(),
                line.getToBinId(),
                line.getLotNumber(),
                line.getSerialNumber(),
                line.getExpiryDate(),
                line.getStockStatus(),
                line.getPlannedQty(),
                line.getActualQty(),
                line.getUnit(),
                line.getStatus(),
                line.isScanConfirmed(),
                line.getExceptionReason(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}

package com.toir.dto.warehouse;

import java.util.UUID;

public record WarehouseTaskScanConfirmRequest(
        UUID scannedBinId,
        UUID scannedSparePartId,
        UUID scannedEquipmentId,
        String lotNumber,
        String serialNumber
) {
}

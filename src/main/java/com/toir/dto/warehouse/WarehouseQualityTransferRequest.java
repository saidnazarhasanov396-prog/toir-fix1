package com.toir.dto.warehouse;

import com.toir.dto.wms.WmsDocumentGroupRequest;
import com.toir.enums.WarehouseStockStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WarehouseQualityTransferRequest(
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus fromStatus,
        WarehouseStockStatus toStatus,
        BigDecimal quantity,
        String reason,
        UUID checkedById,
        String documentNumber,
        List<WmsDocumentGroupRequest> documentGroups,
        boolean strictDocumentPolicy
) {
}

package com.toir.dto.warehouse;

import com.toir.entity.warehouse.WarehouseWriteoffRequest;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseWriteoffStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WarehouseWriteoffRequestDto(
        UUID id,
        String requestNumber,
        UUID warehouseId,
        UUID sparePartId,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        BigDecimal quantity,
        String reason,
        WarehouseWriteoffStatus status,
        UUID requestedById,
        UUID approvedById,
        UUID approvalRequestId,
        UUID stockMovementId,
        String documentNumber,
        String comment
) {
    public static WarehouseWriteoffRequestDto from(WarehouseWriteoffRequest request) {
        return new WarehouseWriteoffRequestDto(
                request.getId(),
                request.getRequestNumber(),
                request.getWarehouseId(),
                request.getSparePartId(),
                request.getBinId(),
                request.getLotNumber(),
                request.getSerialNumber(),
                request.getExpiryDate(),
                request.getStockStatus(),
                request.getQuantity(),
                request.getReason(),
                request.getStatus(),
                request.getRequestedById(),
                request.getApprovedById(),
                request.getApprovalRequestId(),
                request.getStockMovementId(),
                request.getDocumentNumber(),
                request.getComment()
        );
    }
}

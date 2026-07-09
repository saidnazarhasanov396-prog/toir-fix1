package com.toir.dto.warehouse;

import com.toir.entity.warehouse.WarehouseWriteoffRequest;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseWriteoffStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WarehouseWriteoffRequestViewDto(
        UUID id,
        String requestNumber,
        UUID warehouseId,
        String warehouseName,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        UUID binId,
        String binCode,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus,
        BigDecimal quantity,
        String reason,
        WarehouseWriteoffStatus status,
        UUID requestedById,
        String requestedByName,
        UUID approvedById,
        String approvedByName,
        UUID approvalRequestId,
        UUID stockMovementId,
        String documentNumber,
        String comment,
        Instant createdAt,
        Instant updatedAt
) {
    public static WarehouseWriteoffRequestViewDto from(WarehouseWriteoffRequest request,
                                                       String warehouseName,
                                                       String sparePartCode,
                                                       String sparePartName,
                                                       String binCode,
                                                       String requestedByName,
                                                       String approvedByName) {
        return new WarehouseWriteoffRequestViewDto(
                request.getId(),
                request.getRequestNumber(),
                request.getWarehouseId(),
                warehouseName,
                request.getSparePartId(),
                sparePartCode,
                sparePartName,
                request.getBinId(),
                binCode,
                request.getLotNumber(),
                request.getSerialNumber(),
                request.getExpiryDate(),
                request.getStockStatus(),
                request.getQuantity(),
                request.getReason(),
                request.getStatus(),
                request.getRequestedById(),
                requestedByName,
                request.getApprovedById(),
                approvedByName,
                request.getApprovalRequestId(),
                request.getStockMovementId(),
                request.getDocumentNumber(),
                request.getComment(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}

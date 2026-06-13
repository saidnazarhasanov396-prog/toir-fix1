package com.toir.dto.purchaseorder;

import com.toir.entity.PurchaseOrder;
import com.toir.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderDto(
        UUID id,
        String number,
        UUID supplierId,
        String supplierName,
        UUID warehouseId,
        String warehouseName,
        UUID procurementRequestId,
        PurchaseOrderStatus status,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        LocalDate receivedDate,
        BigDecimal totalAmount,
        String comment,
        UUID createdBy,
        Instant createdAt,
        List<PurchaseOrderLineDto> lines
) {
    public static PurchaseOrderDto from(
            PurchaseOrder order,
            String supplierName,
            String warehouseName,
            List<PurchaseOrderLineDto> lines
    ) {
        return new PurchaseOrderDto(
                order.getId(),
                order.getNumber(),
                order.getSupplierId(),
                supplierName,
                order.getWarehouseId(),
                warehouseName,
                order.getProcurementRequestId(),
                order.getStatus(),
                order.getOrderDate(),
                order.getExpectedDeliveryDate(),
                order.getReceivedDate(),
                order.getTotalAmount(),
                order.getComment(),
                order.getCreatedBy(),
                order.getCreatedAt(),
                lines
        );
    }
}

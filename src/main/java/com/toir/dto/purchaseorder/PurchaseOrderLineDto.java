package com.toir.dto.purchaseorder;

import com.toir.entity.PurchaseOrderLine;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderLineDto(
        UUID id,
        UUID sparePartId,
        String sparePartName,
        BigDecimal orderedQuantity,
        BigDecimal receivedQuantity,
        BigDecimal remainingQuantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount
) {
    public static PurchaseOrderLineDto from(PurchaseOrderLine line, String sparePartName) {
        return new PurchaseOrderLineDto(
                line.getId(),
                line.getSparePartId(),
                sparePartName,
                line.getOrderedQuantity(),
                line.getReceivedQuantity(),
                line.getRemainingQuantity(),
                line.getUnitPrice(),
                line.getTotalAmount()
        );
    }
}

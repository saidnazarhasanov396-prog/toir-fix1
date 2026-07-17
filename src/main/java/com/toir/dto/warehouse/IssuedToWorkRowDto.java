package com.toir.dto.warehouse;

import com.toir.repository.IssuedToWorkRowProjection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IssuedToWorkRowDto(
        UUID movementId,
        LocalDate movementDate,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        BigDecimal quantity,
        String unit,
        UUID warehouseId,
        String warehouseName,
        UUID workOrderId,
        String workOrderNumber,
        String workOrderTitle,
        String workOrderStatus,
        UUID issuedById,
        String issuedByName,
        UUID responsiblePersonId,
        String responsiblePersonName,
        String documentNumber,
        String sourceDocumentNo,
        String sourceType,
        UUID sourceId
) {
    public static IssuedToWorkRowDto from(IssuedToWorkRowProjection row) {
        return new IssuedToWorkRowDto(
                row.getMovementId(),
                row.getMovementDate(),
                row.getSparePartId(),
                row.getSparePartCode(),
                row.getSparePartName(),
                row.getQuantity(),
                row.getUnit(),
                row.getWarehouseId(),
                row.getWarehouseName(),
                row.getWorkOrderId(),
                row.getWorkOrderNumber(),
                row.getWorkOrderTitle(),
                row.getWorkOrderStatus(),
                row.getIssuedById(),
                row.getIssuedByName(),
                row.getResponsiblePersonId(),
                row.getResponsiblePersonName(),
                row.getDocumentNumber(),
                row.getSourceDocumentNo(),
                row.getSourceType(),
                row.getSourceId()
        );
    }
}

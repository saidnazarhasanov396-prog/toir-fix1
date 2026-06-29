package com.toir.dto.sparepart;

import com.toir.dto.mxik.MxikRefDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SparePartDetailDto(
        UUID id,
        String code,
        String name,
        String sku,
        UUID typeId,
        String typeCode,
        String typeName,
        String unit,
        String specification,
        String manufacturer,
        UUID mxikId,
        MxikRefDto mxik,
        double totalQuantity,
        double totalReservedQty,
        double totalAvailableQty,
        BigDecimal totalReceivedQuantity,
        BigDecimal totalIssuedQuantity,
        BigDecimal currentQuantity,
        LocalDate lastReceiptDate,
        LocalDate lastIssueDate,
        String lastReceiptDocument,
        String lastIssueDocument,
        List<SparePartLocationDto> locations,
        List<SparePartRecentMovementDto> recentMovements
) {
}

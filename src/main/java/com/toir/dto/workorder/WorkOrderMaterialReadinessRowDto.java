package com.toir.dto.workorder;

import com.toir.enums.MaterialReadinessStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.math.BigDecimal;

public record WorkOrderMaterialReadinessRowDto(
        UUID requirementId,
        UUID sparePartId,
        String sparePartName,
        String unit,
        UUID sourceWarehouseId,
        String sourceWarehouseCode,
        String sourceWarehouseName,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal onHandQty,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal wmsReservedQty,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal availableQty,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal requiredQty,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal reservedQty,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal issuedQty,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal returnedQty,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal shortageQty,
        MaterialReadinessStatus readinessStatus,
        boolean blocking,
        LocalDate expectedDate,
        String sourceSystem,
        Instant lastSyncedAt,
        String nextAction,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal nextActionQty,
        String notes
) {
    public WorkOrderMaterialReadinessRowDto(
            UUID requirementId,
            UUID sparePartId,
            String sparePartName,
            String unit,
            BigDecimal requiredQty,
            BigDecimal reservedQty,
            BigDecimal issuedQty,
            BigDecimal returnedQty,
            BigDecimal shortageQty,
            MaterialReadinessStatus readinessStatus,
            boolean blocking,
            LocalDate expectedDate,
            String sourceSystem,
            Instant lastSyncedAt,
            String notes
    ) {
        this(requirementId, sparePartId, sparePartName, unit,
                null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                requiredQty, reservedQty, issuedQty, returnedQty, shortageQty,
                readinessStatus, blocking, expectedDate, sourceSystem, lastSyncedAt,
                "NONE", BigDecimal.ZERO, notes);
    }
}

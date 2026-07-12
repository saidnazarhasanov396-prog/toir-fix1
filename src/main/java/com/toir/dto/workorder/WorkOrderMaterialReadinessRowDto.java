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
        String notes
) {
}

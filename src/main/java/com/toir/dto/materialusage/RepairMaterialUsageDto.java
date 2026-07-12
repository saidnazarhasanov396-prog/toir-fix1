package com.toir.dto.materialusage;

import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.enums.InventoryItemKind;
import com.toir.enums.WarehouseStockStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.math.BigDecimal;

public record RepairMaterialUsageDto(
        UUID id,
        UUID workOrderId,
        String workOrderNumber,
        String workOrderTitle,
        @NotNull UUID warehouseId,
        String warehouseName,
        @NotNull UUID sparePartId,
        String sparePartName,
        String sparePartCode,
        InventoryItemKind kind,
        @Positive @jakarta.validation.constraints.Digits(integer=15,fraction=4)
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.toir.dto.common.MoneyDecimalStringDeserializer.class)
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal quantity,
        Double unitCost,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.toir.dto.sparepartlifecycle.DecimalStringSerializer.class) BigDecimal totalCost,
        Instant issuedAt,
        UUID issuedById,
        String issuedByName,
        UUID stockMovementId,
        String notes,
        String costWarning,
        UUID requirementId,
        UUID replacedSparePartId,
        String replacedSparePartName,
        String replacedSparePartCode,
        UUID binId,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate,
        WarehouseStockStatus stockStatus
) {
    public RepairMaterialUsageDto {
        if (totalCost == null) {
            totalCost = totalCost(quantity, unitCost);
        }
    }

    public RepairMaterialUsageDto(UUID id,
                                  UUID workOrderId,
                                  UUID warehouseId,
                                  UUID sparePartId,
                                  BigDecimal quantity,
                                  Double unitCost) {
        this(id, workOrderId, null, null, warehouseId, null, sparePartId, null, null, null,
                quantity, unitCost, totalCost(quantity, unitCost), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    public RepairMaterialUsageDto(
            UUID id,
            UUID workOrderId,
            String workOrderNumber,
            String workOrderTitle,
            @NotNull UUID warehouseId,
            String warehouseName,
            @NotNull UUID sparePartId,
            String sparePartName,
            String sparePartCode,
            InventoryItemKind kind,
            @Positive BigDecimal quantity,
            Double unitCost,
            BigDecimal totalCost,
            Instant issuedAt,
            UUID issuedById,
            String issuedByName,
            UUID stockMovementId,
            String notes
    ) {
        this(id, workOrderId, workOrderNumber, workOrderTitle, warehouseId, warehouseName, sparePartId,
                sparePartName, sparePartCode, kind, quantity, unitCost, totalCost, issuedAt, issuedById,
                issuedByName, stockMovementId, notes, null, null, null, null, null,
                null, null, null, null, null);
    }

    public RepairMaterialUsageDto(
            UUID id,
            UUID workOrderId,
            String workOrderNumber,
            String workOrderTitle,
            @NotNull UUID warehouseId,
            String warehouseName,
            @NotNull UUID sparePartId,
            String sparePartName,
            String sparePartCode,
            InventoryItemKind kind,
            @Positive BigDecimal quantity,
            Double unitCost,
            BigDecimal totalCost,
            Instant issuedAt,
            UUID issuedById,
            String issuedByName,
            UUID stockMovementId,
            String notes,
            String costWarning,
            UUID requirementId,
            UUID replacedSparePartId,
            String replacedSparePartName,
            String replacedSparePartCode
    ) {
        this(id, workOrderId, workOrderNumber, workOrderTitle, warehouseId, warehouseName, sparePartId,
                sparePartName, sparePartCode, kind, quantity, unitCost, totalCost, issuedAt, issuedById,
                issuedByName, stockMovementId, notes, costWarning, requirementId, replacedSparePartId,
                replacedSparePartName, replacedSparePartCode, null, null, null, null, null);
    }

    public static RepairMaterialUsageDto from(RepairMaterialUsage u) {
        return new RepairMaterialUsageDto(
                u.getId(),
                u.getWorkOrderId(),
                null, null,
                u.getWarehouseId(), null,
                u.getSparePartId(), null, null, null,
                u.getQuantity(),
                u.getUnitCost(),
                totalCost(u.getQuantity(), u.getUnitCost()),
                u.getIssuedAt() == null ? u.getCreatedAt() : u.getIssuedAt(),
                u.getIssuedById(), null,
                u.getStockMovementId(),
                u.getNotes(),
                costWarning(u.getUnitCost()),
                u.getRequirementId(),
                u.getReplacedSparePartId(),
                null, null,
                u.getBinId(),
                u.getLotNumber(),
                u.getSerialNumber(),
                u.getExpiryDate(),
                u.getStockStatus()
        );
    }

    public static RepairMaterialUsageDto detailed(RepairMaterialUsage u,
                                                  String workOrderNumber,
                                                  String workOrderTitle,
                                                  String warehouseName,
                                                  String sparePartName,
                                                  String sparePartCode,
                                                  InventoryItemKind kind,
                                                  String issuedByName,
                                                  String replacedSparePartName,
                                                  String replacedSparePartCode) {
        return new RepairMaterialUsageDto(
                u.getId(),
                u.getWorkOrderId(),
                workOrderNumber, workOrderTitle,
                u.getWarehouseId(), warehouseName,
                u.getSparePartId(), sparePartName, sparePartCode, kind,
                u.getQuantity(),
                u.getUnitCost(),
                totalCost(u.getQuantity(), u.getUnitCost()),
                u.getIssuedAt() == null ? u.getCreatedAt() : u.getIssuedAt(),
                u.getIssuedById(), issuedByName,
                u.getStockMovementId(),
                u.getNotes(),
                costWarning(u.getUnitCost()),
                u.getRequirementId(),
                u.getReplacedSparePartId(),
                replacedSparePartName,
                replacedSparePartCode,
                u.getBinId(),
                u.getLotNumber(),
                u.getSerialNumber(),
                u.getExpiryDate(),
                u.getStockStatus()
        );
    }

    private static BigDecimal totalCost(BigDecimal quantity, Double unitCost) {
        return unitCost == null||quantity==null ? null : quantity.multiply(new BigDecimal(unitCost.toString()));
    }

    private static String costWarning(Double unitCost) {
        return unitCost == null || unitCost <= 0 ? "Actual cost was not generated because unit cost is unknown." : null;
    }
}

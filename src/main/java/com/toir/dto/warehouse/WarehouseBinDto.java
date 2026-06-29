package com.toir.dto.warehouse;

import com.toir.entity.warehouse.WarehouseBin;
import com.toir.enums.WarehouseQualityZoneType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WarehouseBinDto(
        UUID id,
        UUID warehouseId,
        String code,
        String zone,
        String aisle,
        String rack,
        String shelfLevel,
        String binType,
        BigDecimal maxWeightKg,
        BigDecimal maxVolumeM3,
        WarehouseQualityZoneType qualityZoneType,
        String temperatureZone,
        String hazardClass,
        boolean allowMixedSpareParts,
        boolean allowMixedLots,
        boolean blocked,
        String blockReason,
        Instant blockedAt,
        boolean frozen,
        boolean active,
        Integer travelSequence,
        Integer binLevel,
        String barcode,
        String qrPayload,
        Instant updatedAt
) {
    public static WarehouseBinDto from(WarehouseBin bin) {
        return new WarehouseBinDto(
                bin.getId(),
                bin.getWarehouseId(),
                bin.getCode(),
                bin.getZone(),
                bin.getAisle(),
                bin.getRack(),
                bin.getShelfLevel(),
                bin.getBinType(),
                bin.getMaxWeightKg(),
                bin.getMaxVolumeM3(),
                bin.getQualityZoneType(),
                bin.getTemperatureZone(),
                bin.getHazardClass(),
                bin.isAllowMixedSpareParts(),
                bin.isAllowMixedLots(),
                bin.isBlocked(),
                bin.getBlockReason(),
                bin.getBlockedAt(),
                bin.isFrozen(),
                bin.isActive(),
                bin.getTravelSequence(),
                bin.getBinLevel(),
                bin.getBarcode(),
                bin.getQrPayload(),
                bin.getUpdatedAt()
        );
    }
}

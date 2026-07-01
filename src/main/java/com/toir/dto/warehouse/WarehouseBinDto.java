package com.toir.dto.warehouse;

import com.toir.entity.warehouse.WarehouseBin;
import com.toir.enums.WarehouseBinType;
import com.toir.enums.WarehouseQualityZoneType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WarehouseBinDto(
        UUID id,
        UUID warehouseId,
        String warehouseName,
        String code,
        String zone,
        String aisle,
        String rack,
        String shelfLevel,
        WarehouseBinType binType,
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
    public WarehouseBinDto(
            UUID id,
            UUID warehouseId,
            String code,
            String zone,
            String aisle,
            String rack,
            String shelfLevel,
            WarehouseBinType binType,
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
        this(id, warehouseId, null, code, zone, aisle, rack, shelfLevel, binType, maxWeightKg, maxVolumeM3,
                qualityZoneType, temperatureZone, hazardClass, allowMixedSpareParts, allowMixedLots, blocked,
                blockReason, blockedAt, frozen, active, travelSequence, binLevel, barcode, qrPayload, updatedAt);
    }

    public static WarehouseBinDto from(WarehouseBin bin) {
        return from(bin, null);
    }

    public static WarehouseBinDto from(WarehouseBin bin, String warehouseName) {
        return new WarehouseBinDto(
                bin.getId(),
                bin.getWarehouseId(),
                warehouseName,
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

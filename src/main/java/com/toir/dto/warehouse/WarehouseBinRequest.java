package com.toir.dto.warehouse;

import com.toir.enums.WarehouseBinType;
import com.toir.enums.WarehouseQualityZoneType;

import java.math.BigDecimal;

public record WarehouseBinRequest(
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
        Boolean allowMixedSpareParts,
        Boolean allowMixedLots,
        String barcode,
        String qrPayload,
        Boolean active,
        Integer travelSequence,
        Integer binLevel
) {
}

package com.toir.dto.warehouse;

import com.toir.entity.warehouse.WmsLabelEvent;

import java.time.Instant;
import java.util.UUID;

public record WmsLabelHistoryDto(
        UUID id,
        String labelType,
        UUID targetId,
        String targetCode,
        UUID warehouseId,
        String warehouseName,
        UUID binId,
        String binCode,
        UUID sparePartId,
        String sparePartCode,
        String sparePartName,
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        String payload,
        Instant printedAt
) {
    public static WmsLabelHistoryDto from(WmsLabelEvent event,
                                          String warehouseName,
                                          String binCode,
                                          String sparePartCode,
                                          String sparePartName,
                                          String equipmentCode,
                                          String equipmentName) {
        return new WmsLabelHistoryDto(
                event.getId(),
                event.getLabelType(),
                event.getTargetId(),
                event.getTargetCode(),
                event.getWarehouseId(),
                warehouseName,
                event.getBinId(),
                binCode,
                event.getSparePartId(),
                sparePartCode,
                sparePartName,
                event.getEquipmentId(),
                equipmentCode,
                equipmentName,
                event.getPayload(),
                event.getCreatedAt()
        );
    }
}

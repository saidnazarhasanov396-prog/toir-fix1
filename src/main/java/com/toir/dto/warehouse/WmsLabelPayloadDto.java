package com.toir.dto.warehouse;

import java.util.UUID;

public record WmsLabelPayloadDto(
        String type,
        UUID id,
        String code,
        UUID warehouseId,
        UUID binId,
        UUID sparePartId,
        UUID equipmentId,
        String payload
) {
}

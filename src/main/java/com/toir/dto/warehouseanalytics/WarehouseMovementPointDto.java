package com.toir.dto.warehouseanalytics;

import java.time.LocalDate;

public record WarehouseMovementPointDto(
        LocalDate bucket,
        double stock,
        double receipt,
        double issue,
        double reserved
) {
}

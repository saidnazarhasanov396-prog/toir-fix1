package com.toir.dto.warehouseanalytics;

import java.util.List;

public record WarehouseAnalyticsOverviewDto(
        List<WarehouseAnalyticsKpiDto> kpis,
        List<WarehouseMovementPointDto> movements,
        List<WarehouseRiskDto> risks,
        List<WarehouseDistributionRowDto> warehouseDistribution,
        List<WarehouseDeficitRowDto> deficits,
        List<WarehouseConsumptionRowDto> topConsumption,
        List<WarehouseReservationRowDto> reservations,
        List<WarehouseAbcXyzCellDto> abcXyz
) {
}

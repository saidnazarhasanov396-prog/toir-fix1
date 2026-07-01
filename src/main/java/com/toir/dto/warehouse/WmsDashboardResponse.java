package com.toir.dto.warehouse;

import java.math.BigDecimal;
import java.util.List;

public record WmsDashboardResponse(
        int healthScore,
        WarehouseTaskStatsResponse tasks,
        InventoryCountStatsResponse inventoryCounts,
        WarehouseBinStatsResponse bins,
        WarehouseStockStatsResponse stock,
        WarehouseQualityStatsResponse quality,
        WarehouseWriteoffStatsResponse writeoffs,
        WarehouseStockMoveStatsResponse stockMoves,
        WmsLabelStatsResponse labels,
        List<WarehouseTaskDto> recentTasks,
        List<WarehouseStockMoveJournalDto> recentMoves,
        List<WarehouseQualityTransferHistoryDto> recentQualityTransfers,
        List<ReconciliationAlert> reconciliationAlerts
) {
    public record ReconciliationAlert(
            String severity,
            String title,
            String description,
            BigDecimal variance
    ) {
    }
}

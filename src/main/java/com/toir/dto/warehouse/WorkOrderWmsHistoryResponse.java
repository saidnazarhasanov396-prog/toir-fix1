package com.toir.dto.warehouse;

import java.util.List;

public record WorkOrderWmsHistoryResponse(
        List<WarehouseTaskDto> tasks,
        List<WarehouseStockMoveJournalDto> stockMovements
) {
}

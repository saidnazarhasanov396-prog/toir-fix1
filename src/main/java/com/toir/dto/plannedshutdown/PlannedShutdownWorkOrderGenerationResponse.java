package com.toir.dto.plannedshutdown;

import com.toir.dto.workorder.WorkOrderDto;
import java.util.List;
import java.util.UUID;

public record PlannedShutdownWorkOrderGenerationResponse(UUID plannedShutdownId, Long windowVersion,
                                                          List<WorkOrderDto> workOrders) {
    public PlannedShutdownWorkOrderGenerationResponse {
        workOrders = List.copyOf(workOrders);
    }
}

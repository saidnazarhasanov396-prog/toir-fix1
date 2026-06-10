package com.toir.dto.dashboard;

import java.util.List;
import java.util.UUID;

public record WorkOrdersByEquipmentTypeResponse(
        UUID departmentId,
        String statusScope,
        List<String> statuses,
        long totalWorkOrders,
        List<Item> items
) {
    public record Item(
            UUID equipmentTypeId,
            String equipmentTypeName,
            long workOrderCount
    ) {}
}

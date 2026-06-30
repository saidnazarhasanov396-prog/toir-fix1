package com.toir.dto.maintenanceworkspace;

public record MaintenanceDispatcherSummary(
        long total,
        long emergencyRepairRequests,
        long newDefects,
        long unassignedRepairRequests,
        long blockedWorkOrders,
        long overdueItems,
        long dueEvents,
        long escalatedItems
) {}

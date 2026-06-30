package com.toir.dto.maintenanceworkspace;

import java.util.List;

public record MaintenanceDispatcherQueues(
        MaintenanceDispatcherSummary summary,
        List<MaintenanceWorkspaceItem> emergencyRepairRequests,
        List<MaintenanceWorkspaceItem> newDefects,
        List<MaintenanceWorkspaceItem> unassignedRepairRequests,
        List<MaintenanceWorkspaceItem> blockedWorkOrders,
        List<MaintenanceWorkspaceItem> overdueItems,
        List<MaintenanceWorkspaceItem> dueEvents,
        List<MaintenanceWorkspaceItem> escalatedItems
) {}

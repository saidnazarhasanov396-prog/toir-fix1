package com.toir.dto.maintenanceworkspace;

public record MaintenancePlannerCapacityResponse(
        long openWorkOrders,
        long scheduledWorkOrders,
        long unscheduledWorkOrders,
        long overloadedBrigades,
        String note
) {}

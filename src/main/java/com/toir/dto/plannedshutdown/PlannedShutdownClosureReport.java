package com.toir.dto.plannedshutdown;

import com.toir.enums.PlannedShutdownWorkItemSourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlannedShutdownClosureReport(
        UUID plannedShutdownId,
        String code,
        Long scopeVersion,
        Long windowVersion,
        Instant plannedStartAt,
        Instant plannedEndAt,
        Instant effectiveExtensionEndAt,
        Instant actualShutdownAt,
        Instant actualSafeStateAt,
        Instant actualRepairStartAt,
        Instant actualTestingStartAt,
        Instant actualStartupAt,
        Instant actualCompletedAt,
        long plannedDowntimeMinutes,
        long actualDowntimeMinutes,
        List<SourceFact> sources,
        List<UUID> sourceIds,
        List<WorkOrderFact> workOrders,
        Map<String, Long> workOrderStatusCounts,
        List<UUID> materialUsageIds,
        List<UUID> actualCostIds,
        List<UUID> defectIds,
        List<ExtensionFact> extensions,
        List<PlannedShutdownStartupTestResponse> startupTests,
        PlannedShutdownProductionReturnResponse productionReturn,
        UUID closedById,
        Instant closedAt) {

    public record SourceFact(PlannedShutdownWorkItemSourceType type, UUID id) {}
    public record WorkOrderFact(UUID id, String status, UUID defectId, String result) {}
    public record ExtensionFact(Instant occurredAt, Instant oldEndAt, Instant newEndAt, UUID actorId, String reason) {}
}

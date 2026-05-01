package com.toir.dto.ops;

public record OpsMetricsResponse(
        String timestamp,
        Counts counts,
        String status
) {
    public record Counts(
            long equipment,
            long defectsOpen,
            long repairRequestsOpen,
            long workOrdersOpen,
            long pprTasksPlanned,
            long pprTasksOverdue,
            long procurementDraft,
            long brigades,
            long conditionReadings,
            long conditionAlarms,
            long activeCertifications,
            long expiredCertifications,
            long calibrationRecords,
            long inspectionRoutes,
            long inspectionRounds,
            long rcmSnapshots,
            long notifications,
            long webhookDeliveries
    ) {
    }
}

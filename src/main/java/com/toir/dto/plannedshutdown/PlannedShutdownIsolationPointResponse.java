package com.toir.dto.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownIsolationPoint;
import com.toir.enums.PlannedShutdownItemStatus;
import java.time.Instant;
import java.util.UUID;

public record PlannedShutdownIsolationPointResponse(UUID id, UUID equipmentId, UUID locationId,
        String isolationMethod, String lockTagIdentifier, UUID responsibleEmployeeId,
        PlannedShutdownItemStatus status, UUID permitId, UUID appliedById, Instant appliedAt,
        UUID verifiedById, Instant verifiedAt, UUID releasedById, Instant releasedAt, Integer orderNumber) {
    public static PlannedShutdownIsolationPointResponse from(PlannedShutdownIsolationPoint point) {
        return new PlannedShutdownIsolationPointResponse(point.getId(), point.getEquipmentId(), point.getLocationId(),
                point.getIsolationMethod(), point.getLockTagIdentifier(), point.getResponsibleEmployeeId(),
                point.getStatus(), point.getPermitId(), point.getAppliedById(), point.getAppliedAt(),
                point.getVerifiedById(), point.getVerifiedAt(), point.getReleasedById(), point.getReleasedAt(),
                point.getOrderNumber());
    }
}

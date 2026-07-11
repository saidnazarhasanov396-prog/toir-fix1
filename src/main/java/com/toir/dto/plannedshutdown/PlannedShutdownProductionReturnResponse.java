package com.toir.dto.plannedshutdown;

import com.toir.entity.plannedshutdown.PlannedShutdownProductionReturn;
import java.time.Instant;
import java.util.UUID;

public record PlannedShutdownProductionReturnResponse(
        UUID id, UUID plannedShutdownId, Long scopeVersion, Long windowVersion,
        UUID approvedById, Instant approvedAt, String evidence) {
    public static PlannedShutdownProductionReturnResponse from(PlannedShutdownProductionReturn signoff) {
        return new PlannedShutdownProductionReturnResponse(signoff.getId(), signoff.getPlannedShutdownId(),
                signoff.getScopeVersion(), signoff.getWindowVersion(),
                signoff.getApprovedById(), signoff.getApprovedAt(), signoff.getEvidence());
    }
}

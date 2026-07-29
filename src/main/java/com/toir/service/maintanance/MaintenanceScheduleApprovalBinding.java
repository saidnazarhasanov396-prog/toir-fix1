package com.toir.service.maintanance;

import java.util.UUID;

public record MaintenanceScheduleApprovalBinding(
        UUID planId,
        long calculationRevision,
        String calculationContentHash,
        int calculationContentHashVersion,
        String requesterContextFingerprint
) {
}

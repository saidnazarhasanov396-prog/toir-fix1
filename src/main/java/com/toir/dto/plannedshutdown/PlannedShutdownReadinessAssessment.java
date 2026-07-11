package com.toir.dto.plannedshutdown;

import java.util.List;

public record PlannedShutdownReadinessAssessment(boolean canProceed, List<PlannedShutdownBlocker> blockers) {
}

package com.toir.dto.plannedshutdown;

import java.util.List;

public record PlannedShutdownNextActionResponse(
        String code,
        String commandEndpoint,
        String targetTab,
        boolean blocked,
        List<String> blockerCodes
) {
}

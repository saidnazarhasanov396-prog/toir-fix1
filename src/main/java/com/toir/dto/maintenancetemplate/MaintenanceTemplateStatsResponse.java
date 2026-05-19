package com.toir.dto.maintenancetemplate;

public record MaintenanceTemplateStatsResponse(
        long totalTemplates,
        long withOperations,
        long totalOperations,
        float avgPerTemplate
) {
}

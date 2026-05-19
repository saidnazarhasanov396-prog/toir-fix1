package com.toir.repository.maintenance;

public interface MaintenanceTemplateStatsProjection {
    Long getTotalTemplates();
    Long getWithOperations();
    Long getTotalOperations();
    Double getAvgOperationsPerTemplate();
}

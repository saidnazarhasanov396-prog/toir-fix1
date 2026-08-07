package com.toir.repository.maintenance;

import java.util.List;
import java.util.UUID;

public interface MaintenanceTemplateSemanticSearchRepository {

    List<ScoredTemplate> findTopTemplates(SearchVector query);

    record SearchVector(
            List<? extends Number> values,
            String modelName,
            String modelRevision,
            int dimension,
            String sourceSchemaVersion,
            int limit
    ) {
    }

    record ScoredTemplate(UUID maintenanceTemplateId, double similarityScore) {
    }
}

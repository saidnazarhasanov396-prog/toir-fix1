package com.toir.dto.maintenanceembedding;

import java.util.List;
import java.util.UUID;

public record MaintenanceTemplateSemanticSearchResponse(
        int count,
        List<Item> items
) {
    public MaintenanceTemplateSemanticSearchResponse {
        items = List.copyOf(items);
        count = items.size();
    }

    public record Item(UUID maintenanceTemplateId, double similarityScore) {
    }
}

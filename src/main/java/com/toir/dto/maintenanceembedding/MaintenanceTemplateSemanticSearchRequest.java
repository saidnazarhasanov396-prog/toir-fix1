package com.toir.dto.maintenanceembedding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MaintenanceTemplateSemanticSearchRequest(
        @NotBlank(message = "query is required")
        @Size(max = 4000, message = "query must not exceed 4000 characters")
        String query
) {
}

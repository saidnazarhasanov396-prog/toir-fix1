package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import com.toir.dto.maintenanceembedding.MaintenanceTemplateSemanticSearchResponse;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceTemplateSemanticSearchRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "toir.ai.maintenance-action-semantic-search",
        name = {"enabled", "semantic-search-enabled"},
        havingValue = "true")
public class MaintenanceTemplateSemanticSearchService {

    private final MaintenanceActionEmbeddingClient embeddingClient;
    private final MaintenanceTemplateSemanticSearchRepository repository;
    private final MaintenanceActionSemanticSearchProperties properties;

    public MaintenanceTemplateSemanticSearchService(
            MaintenanceActionEmbeddingClient embeddingClient,
            MaintenanceTemplateSemanticSearchRepository repository,
            MaintenanceActionSemanticSearchProperties properties
    ) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.properties = properties;
    }

    public MaintenanceTemplateSemanticSearchResponse search(String query) {
        if (!properties.isEnabled() || !properties.isSemanticSearchEnabled()) {
            throw new RestException("Maintenance Template semantic search is disabled",
                    HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE_TEMPLATE_SEMANTIC_SEARCH_DISABLED");
        }
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            throw RestException.badRequest("query is required", "SEMANTIC_SEARCH_QUERY_REQUIRED");
        }

        MaintenanceActionEmbeddingClient.EmbeddingResult queryEmbedding;
        try {
            queryEmbedding = embeddingClient.embed(new MaintenanceActionEmbeddingClient.EmbeddingInput(
                    normalizedQuery,
                    MaintenanceActionEmbeddingClient.InputMode.QUERY,
                    properties.getModelName(),
                    properties.getModelRevision(),
                    properties.getDimension()));
        } catch (EmbeddingServiceException exception) {
            if (!exception.isRetryable()) {
                throw RestException.badRequest("Query cannot be embedded", "SEMANTIC_SEARCH_QUERY_REJECTED");
            }
            throw new RestException("Embedding service is unavailable", HttpStatus.SERVICE_UNAVAILABLE,
                    "SEMANTIC_SEARCH_EMBEDDING_UNAVAILABLE");
        }

        var rows = repository.findTopTemplates(new MaintenanceTemplateSemanticSearchRepository.SearchVector(
                queryEmbedding.values(),
                properties.getModelName(),
                properties.getModelRevision(),
                properties.getDimension(),
                MaintenanceActionEmbeddingTextBuilder.SOURCE_SCHEMA_VERSION,
                10));
        return new MaintenanceTemplateSemanticSearchResponse(0, rows.stream()
                .map(row -> new MaintenanceTemplateSemanticSearchResponse.Item(
                        row.maintenanceTemplateId(), row.similarityScore()))
                .toList());
    }
}

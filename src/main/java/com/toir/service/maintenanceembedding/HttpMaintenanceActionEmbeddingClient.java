package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Objects;

public final class HttpMaintenanceActionEmbeddingClient implements MaintenanceActionEmbeddingClient {

    private final MaintenanceActionSemanticSearchProperties properties;
    private final RestClient restClient;

    public HttpMaintenanceActionEmbeddingClient(
            MaintenanceActionSemanticSearchProperties properties,
            RestClient restClient
    ) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public EmbeddingResult embed(EmbeddingInput input) {
        validateInput(input);
        try {
            EmbedResponse response = restClient.post()
                    .uri(properties.getEndpointPath())
                    .headers(headers -> {
                        if (StringUtils.hasText(properties.getAuthenticationHeader())) {
                            headers.set(properties.getAuthenticationHeader(), properties.getAuthenticationSecret());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new EmbedRequest(List.of(input.text())))
                    .retrieve()
                    .body(EmbedResponse.class);
            return validatedResult(input, response);
        } catch (EmbeddingServiceException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw failure("EMBEDDING_SERVICE_TIMEOUT", "Embedding service timed out or is unavailable", true);
        } catch (RestClientResponseException exception) {
            throw mapStatus(exception.getStatusCode());
        } catch (RestClientException exception) {
            throw failure("EMBEDDING_SERVICE_MALFORMED_RESPONSE", "Embedding service response is invalid", true);
        }
    }

    private EmbeddingResult validatedResult(EmbeddingInput input, EmbedResponse response) {
        if (response == null || response.embeddings() == null || response.embeddings().size() != 1) {
            throw failure("EMBEDDING_COUNT_MISMATCH", "Embedding service returned an unexpected result count", false);
        }
        List<Double> values = response.embeddings().getFirst();
        try {
            EmbeddingVectorValidator.requireFiniteDimension(values, input.expectedDimension());
        } catch (EmbeddingVectorValidator.InvalidEmbeddingException exception) {
            throw failure("INVALID_EMBEDDING_VECTOR", "Embedding service returned an invalid vector", false);
        }
        return new EmbeddingResult(List.copyOf(values), input.modelName(), input.modelRevision(), input.mode());
    }

    private void validateInput(EmbeddingInput input) {
        if (input == null || !StringUtils.hasText(input.text())) {
            throw new IllegalArgumentException("Embedding input text is required");
        }
        if (properties.getBatchSize() != 1) {
            throw new IllegalStateException("Embedding batch size must remain 1 until response ordering is confirmed");
        }
        if (!Objects.equals(properties.getModelName(), input.modelName())
                || !Objects.equals(properties.getModelRevision(), input.modelRevision())
                || properties.getDimension() != input.expectedDimension()) {
            throw failure("EMBEDDING_CONTRACT_MISMATCH", "Embedding request is incompatible with configured model metadata", false);
        }
    }

    private EmbeddingServiceException mapStatus(HttpStatusCode status) {
        if (status.value() == 422) {
            return failure("EMBEDDING_SERVICE_VALIDATION_ERROR", "Embedding service rejected the input", false);
        }
        if (status.is5xxServerError()) {
            return failure("EMBEDDING_SERVICE_UNAVAILABLE", "Embedding service is unavailable", true);
        }
        return failure("EMBEDDING_SERVICE_HTTP_ERROR", "Embedding service request failed", false);
    }

    private static EmbeddingServiceException failure(String code, String message, boolean retryable) {
        return new EmbeddingServiceException(code, message, retryable);
    }

    private record EmbedRequest(List<String> texts) {
    }

    private record EmbedResponse(List<List<Double>> embeddings) {
    }
}

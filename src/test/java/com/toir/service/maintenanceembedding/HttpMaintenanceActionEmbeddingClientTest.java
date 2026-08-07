package com.toir.service.maintenanceembedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.config.MaintenanceActionSemanticSearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpMaintenanceActionEmbeddingClientTest {

    private static final String ENDPOINT = "/ai/recurrent_failure_analysis/recurrent-failure-analysis/embed";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MaintenanceActionSemanticSearchProperties properties;
    private MockRestServiceServer server;
    private HttpMaintenanceActionEmbeddingClient client;

    @BeforeEach
    void setUp() {
        properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://toir-ai.example");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpMaintenanceActionEmbeddingClient(properties, builder.build());
    }

    @Test
    void mapsRealTextsEmbeddingsContractAndValidates768Values() throws Exception {
        List<Double> vector = new ArrayList<>(Collections.nCopies(768, 0.125d));
        server.expect(once(), requestTo("https://toir-ai.example" + ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"texts\":[\"Nasos podshipnigi\"]}"))
                .andRespond(withSuccess(json(Map.of("embeddings", List.of(vector))), MediaType.APPLICATION_JSON));

        var result = client.embed(input("Nasos podshipnigi"));

        assertThat(result.values()).hasSize(768);
        assertThat(result.modelRevision()).isEqualTo("immutable-test-revision");
        assertThat(result.mode()).isEqualTo(MaintenanceActionEmbeddingClient.InputMode.QUERY);
        server.verify();
    }

    @Test
    void rejectsResponseCountMismatch() throws Exception {
        server.expect(requestTo("https://toir-ai.example" + ENDPOINT))
                .andRespond(withSuccess(json(Map.of("embeddings", List.of())), MediaType.APPLICATION_JSON));

        assertFailure(input("query"), "EMBEDDING_COUNT_MISMATCH", false);
    }

    @Test
    void rejectsWrongVectorDimension() throws Exception {
        server.expect(requestTo("https://toir-ai.example" + ENDPOINT))
                .andRespond(withSuccess(json(Map.of("embeddings", List.of(List.of(0.1d, 0.2d)))),
                        MediaType.APPLICATION_JSON));

        assertFailure(input("query"), "INVALID_EMBEDDING_VECTOR", false);
    }

    @Test
    void rejectsMalformedResponseSafely() {
        server.expect(requestTo("https://toir-ai.example" + ENDPOINT))
                .andRespond(withSuccess("{malformed", MediaType.APPLICATION_JSON));

        assertFailure(input("query"), "EMBEDDING_SERVICE_MALFORMED_RESPONSE", true);
    }

    @Test
    void maps422AsPermanentAnd5xxAsRetryableWithoutLeakingResponseBody() {
        server.expect(requestTo("https://toir-ai.example" + ENDPOINT))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("secret diagnostic")
                        .contentType(MediaType.TEXT_PLAIN));
        assertFailure(input("query"), "EMBEDDING_SERVICE_VALIDATION_ERROR", false);

        server.reset();
        server.expect(requestTo("https://toir-ai.example" + ENDPOINT)).andRespond(withServerError());
        assertFailure(input("query"), "EMBEDDING_SERVICE_UNAVAILABLE", true);
    }

    @Test
    void mapsTimeoutAsRetryableSafeFailure() {
        server.expect(requestTo("https://toir-ai.example" + ENDPOINT))
                .andRespond(withException(new SocketTimeoutException("contains-secret")));

        assertFailure(input("query"), "EMBEDDING_SERVICE_TIMEOUT", true);
    }

    @Test
    void rejectsBatchSizeAboveOneUntilOrderingIsConfirmed() {
        properties.setBatchSize(2);

        assertThatThrownBy(() -> client.embed(input("query")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batch size must remain 1");
    }

    private void assertFailure(
            MaintenanceActionEmbeddingClient.EmbeddingInput input,
            String errorCode,
            boolean retryable
    ) {
        assertThatThrownBy(() -> client.embed(input))
                .isInstanceOfSatisfying(EmbeddingServiceException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(errorCode);
                    assertThat(exception.isRetryable()).isEqualTo(retryable);
                    assertThat(exception.getMessage()).doesNotContain("secret");
                });
    }

    private MaintenanceActionEmbeddingClient.EmbeddingInput input(String text) {
        return new MaintenanceActionEmbeddingClient.EmbeddingInput(
                text,
                MaintenanceActionEmbeddingClient.InputMode.QUERY,
                properties.getModelName(),
                properties.getModelRevision(),
                properties.getDimension());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static MaintenanceActionSemanticSearchProperties properties() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        properties.setServiceBaseUrl("https://toir-ai.example");
        properties.setEndpointPath(ENDPOINT);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setBatchSize(1);
        properties.setModelRevision("immutable-test-revision");
        properties.setModelDimensionContractConfirmed(true);
        return properties;
    }
}

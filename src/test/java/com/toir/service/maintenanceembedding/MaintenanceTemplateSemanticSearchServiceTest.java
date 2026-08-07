package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import com.toir.exception.RestException;
import com.toir.repository.maintenance.MaintenanceTemplateSemanticSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceTemplateSemanticSearchServiceTest {

    @Mock
    private MaintenanceActionEmbeddingClient client;

    @Mock
    private MaintenanceTemplateSemanticSearchRepository repository;

    private MaintenanceTemplateSemanticSearchService service;

    @BeforeEach
    void setUp() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        properties.setEnabled(true);
        properties.setSemanticSearchEnabled(true);
        properties.setModelRevision("revision-42");
        service = new MaintenanceTemplateSemanticSearchService(client, repository, properties);
    }

    @Test
    void returnsAtMostTenUniqueTemplateScoresWithoutVectors() {
        List<Double> queryVector = Collections.nCopies(768, 0.25d);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(client.embed(any())).thenReturn(new MaintenanceActionEmbeddingClient.EmbeddingResult(
                queryVector, "model", "revision-42", MaintenanceActionEmbeddingClient.InputMode.QUERY));
        when(repository.findTopTemplates(any())).thenReturn(List.of(
                new MaintenanceTemplateSemanticSearchRepository.ScoredTemplate(first, 0.9231),
                new MaintenanceTemplateSemanticSearchRepository.ScoredTemplate(second, 0.8874)));

        var response = service.search(" Nasos podshipnigini almashtirish ");

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.items()).extracting(item -> item.maintenanceTemplateId())
                .containsExactly(first, second);
        assertThat(response.toString()).doesNotContain("0.25");
        verify(repository).findTopTemplates(org.mockito.ArgumentMatchers.argThat(query ->
                query.limit() == 10 && query.modelRevision().equals("revision-42")));
    }

    @Test
    void zeroEligibleTemplatesReturnsHttpCompatibleEmptyPayload() {
        when(client.embed(any())).thenReturn(new MaintenanceActionEmbeddingClient.EmbeddingResult(
                Collections.nCopies(768, 0.25d), "model", "revision-42",
                MaintenanceActionEmbeddingClient.InputMode.QUERY));
        when(repository.findTopTemplates(any())).thenReturn(List.of());

        var response = service.search("query");

        assertThat(response.count()).isZero();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void transientEmbeddingFailureMapsToControlled503() {
        when(client.embed(any())).thenThrow(new EmbeddingServiceException(
                "EMBEDDING_SERVICE_UNAVAILABLE", "safe", true));

        assertThatThrownBy(() -> service.search("query"))
                .isInstanceOfSatisfying(RestException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getErrorCode()).isEqualTo("SEMANTIC_SEARCH_EMBEDDING_UNAVAILABLE");
                });
    }
}

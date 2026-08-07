package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceActionEmbeddingWorkerTest {

    @Mock
    private MaintenanceActionEmbeddingJobStore store;

    @Mock
    private MaintenanceActionEmbeddingClient client;

    private MaintenanceActionEmbeddingWorker worker;

    @BeforeEach
    void setUp() {
        MaintenanceActionSemanticSearchProperties properties = new MaintenanceActionSemanticSearchProperties();
        properties.setRetryBaseBackoff(Duration.ofSeconds(1));
        properties.setRetryMaxBackoff(Duration.ofSeconds(10));
        properties.setRetryJitter(0);
        worker = new MaintenanceActionEmbeddingWorker(store, client, properties);
    }

    @Test
    void claimedImmutableRepresentationBecomesReadyOnlyThroughVectorPersistence() {
        var job = job();
        List<Double> vector = Collections.nCopies(768, 0.5d);
        when(client.embed(any())).thenReturn(new MaintenanceActionEmbeddingClient.EmbeddingResult(
                vector, job.modelName(), job.modelRevision(), MaintenanceActionEmbeddingClient.InputMode.DOCUMENT));

        worker.process(job);

        verify(client).embed(org.mockito.ArgumentMatchers.argThat(input ->
                input.text().equals(job.normalizedSourceText())
                        && input.modelRevision().equals(job.modelRevision())));
        verify(store).persistVectorAndMarkReady(job.id(), job.leaseToken(), vector);
        verify(store, never()).recordRetry(any(), any(), any(), any());
    }

    @Test
    void transientAiFailureSchedulesBoundedRetryAndCannotPublishReady() {
        var job = job();
        when(client.embed(any())).thenThrow(new EmbeddingServiceException("EMBEDDING_SERVICE_TIMEOUT", "safe", true));

        worker.process(job);

        verify(store).recordRetry(org.mockito.ArgumentMatchers.eq(job.id()),
                org.mockito.ArgumentMatchers.eq(job.leaseToken()),
                org.mockito.ArgumentMatchers.eq("EMBEDDING_SERVICE_TIMEOUT"), any());
        verify(store, never()).persistVectorAndMarkReady(any(), any(), any());
    }

    @Test
    void vectorPersistenceFailureSchedulesRetryAndNeverMarksReadySeparately() {
        var job = job();
        List<Double> vector = Collections.nCopies(768, 0.5d);
        when(client.embed(any())).thenReturn(new MaintenanceActionEmbeddingClient.EmbeddingResult(
                vector, job.modelName(), job.modelRevision(), MaintenanceActionEmbeddingClient.InputMode.DOCUMENT));
        when(store.persistVectorAndMarkReady(job.id(), job.leaseToken(), vector))
                .thenThrow(new IllegalStateException("database unavailable"));

        worker.process(job);

        verify(store).recordRetry(org.mockito.ArgumentMatchers.eq(job.id()),
                org.mockito.ArgumentMatchers.eq(job.leaseToken()),
                org.mockito.ArgumentMatchers.eq("EMBEDDING_VECTOR_PERSISTENCE_FAILED"), any());
    }

    @Test
    void lostFenceCannotPublishObsoleteReadyOrScheduleDuplicateRetry() {
        var job = job();
        List<Double> vector = Collections.nCopies(768, 0.5d);
        when(client.embed(any())).thenReturn(new MaintenanceActionEmbeddingClient.EmbeddingResult(
                vector, job.modelName(), job.modelRevision(), MaintenanceActionEmbeddingClient.InputMode.DOCUMENT));
        when(store.persistVectorAndMarkReady(job.id(), job.leaseToken(), vector)).thenReturn(false);

        worker.process(job);

        verify(store).persistVectorAndMarkReady(job.id(), job.leaseToken(), vector);
        verify(store, never()).recordRetry(any(), any(), any(), any());
        verify(store, never()).markTerminalFailure(any(), any(), any());
    }

    private static MaintenanceActionEmbeddingJobStore.ClaimedJob job() {
        return new MaintenanceActionEmbeddingJobStore.ClaimedJob(
                UUID.randomUUID(), UUID.randomUUID(), "immutable queued text", "a".repeat(64),
                "ibm-granite/granite-embedding-311m-multilingual-r2", "revision-42", 768,
                "maintenance-action-text-v1", 1, 3, UUID.randomUUID());
    }
}

package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(
        prefix = "toir.ai.maintenance-action-semantic-search",
        name = {"enabled", "generation-worker-enabled"},
        havingValue = "true")
public class MaintenanceActionEmbeddingWorker {

    private final MaintenanceActionEmbeddingJobStore jobStore;
    private final MaintenanceActionEmbeddingClient client;
    private final MaintenanceActionSemanticSearchProperties properties;
    private final String leaseOwner = "maintenance-action-embedding-" + UUID.randomUUID();

    public MaintenanceActionEmbeddingWorker(
            MaintenanceActionEmbeddingJobStore jobStore,
            MaintenanceActionEmbeddingClient client,
            MaintenanceActionSemanticSearchProperties properties
    ) {
        this.jobStore = jobStore;
        this.client = client;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${toir.ai.maintenance-action-semantic-search.polling-interval:PT1M}")
    public void poll() {
        if (!properties.isEnabled() || !properties.isGenerationWorkerEnabled()) {
            return;
        }
        for (int index = 0; index < properties.getClientConcurrency(); index++) {
            Instant now = Instant.now();
            List<MaintenanceActionEmbeddingJobStore.ClaimedJob> jobs = jobStore.claimEligible(
                    1, leaseOwner, now.plus(properties.getLeaseDuration()));
            if (jobs.isEmpty()) {
                return;
            }
            process(jobs.getFirst());
        }
    }

    void process(MaintenanceActionEmbeddingJobStore.ClaimedJob job) {
        try {
            MaintenanceActionEmbeddingClient.EmbeddingResult result = client.embed(
                    new MaintenanceActionEmbeddingClient.EmbeddingInput(
                            job.normalizedSourceText(),
                            MaintenanceActionEmbeddingClient.InputMode.DOCUMENT,
                            job.modelName(),
                            job.modelRevision(),
                            job.dimension()));
            jobStore.persistVectorAndMarkReady(job.id(), job.leaseToken(), result.values());
        } catch (EmbeddingServiceException exception) {
            handleFailure(job, exception.getErrorCode(), exception.isRetryable());
        } catch (RuntimeException exception) {
            handleFailure(job, "EMBEDDING_VECTOR_PERSISTENCE_FAILED", true);
        }
    }

    private void handleFailure(
            MaintenanceActionEmbeddingJobStore.ClaimedJob job,
            String safeErrorCode,
            boolean retryable
    ) {
        if (!retryable) {
            jobStore.markTerminalFailure(job.id(), job.leaseToken(), safeErrorCode);
            return;
        }
        jobStore.recordRetry(job.id(), job.leaseToken(), safeErrorCode,
                Instant.now().plus(backoff(job.attemptCount())));
    }

    private Duration backoff(int attemptCount) {
        Duration base = properties.getRetryBaseBackoff();
        Duration maximum = properties.getRetryMaxBackoff();
        long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 20);
        long boundedMillis;
        try {
            boundedMillis = Math.min(Math.multiplyExact(base.toMillis(), multiplier), maximum.toMillis());
        } catch (ArithmeticException overflow) {
            boundedMillis = maximum.toMillis();
        }
        double jitter = properties.getRetryJitter();
        if (jitter > 0) {
            double factor = ThreadLocalRandom.current().nextDouble(1.0 - jitter, 1.0 + jitter);
            boundedMillis = Math.min(maximum.toMillis(), Math.max(1L, Math.round(boundedMillis * factor)));
        }
        return Duration.ofMillis(Math.max(1L, boundedMillis));
    }
}

package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import com.toir.entity.maintenance.MaintenanceAction;
import com.toir.repository.maintenance.MaintenanceActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MaintenanceActionEmbeddingBackfillService {

    private final MaintenanceActionEmbeddingBackfillStore backfillStore;
    private final MaintenanceActionRepository actionRepository;
    private final DurableMaintenanceActionEmbeddingLifecycle lifecycle;
    private final MaintenanceActionSemanticSearchProperties properties;

    public MaintenanceActionEmbeddingBackfillService(
            MaintenanceActionEmbeddingBackfillStore backfillStore,
            MaintenanceActionRepository actionRepository,
            DurableMaintenanceActionEmbeddingLifecycle lifecycle,
            MaintenanceActionSemanticSearchProperties properties
    ) {
        this.backfillStore = backfillStore;
        this.actionRepository = actionRepository;
        this.lifecycle = lifecycle;
        this.properties = properties;
    }

    @Transactional
    public MaintenanceActionEmbeddingBackfillStore.BackfillRun start(
            String idempotencyKey,
            Integer requestedBatchSize,
            UUID requestedBy
    ) {
        requireEnabled();
        int batchSize = requestedBatchSize == null
                ? properties.getBackfill().getDefaultBatchSize()
                : requestedBatchSize;
        if (batchSize < properties.getBackfill().getMinimumBatchSize()
                || batchSize > properties.getBackfill().getMaximumBatchSize()) {
            throw new IllegalArgumentException("Backfill batch size is outside configured bounds");
        }
        return backfillStore.start(new MaintenanceActionEmbeddingBackfillStore.StartCommand(
                idempotencyKey,
                properties.getModelName(),
                properties.getModelRevision(),
                properties.getDimension(),
                MaintenanceActionEmbeddingTextBuilder.SOURCE_SCHEMA_VERSION,
                batchSize,
                requestedBy));
    }

    @Transactional
    public MaintenanceActionEmbeddingBackfillStore.BackfillRun processNextBatch(UUID runId) {
        requireEnabled();
        MaintenanceActionEmbeddingBackfillStore.BackfillRun run = backfillStore.lockForBatch(runId);
        if (run.status() == MaintenanceActionBackfillStatus.REQUESTED) {
            run = backfillStore.transition(runId, MaintenanceActionBackfillStatus.RUNNING);
        }
        if (run.status() != MaintenanceActionBackfillStatus.RUNNING) {
            return run;
        }

        List<MaintenanceAction> actions = actionRepository.findEmbeddingBackfillBatch(run.cursor(), run.batchSize());
        long alreadyPresent = 0;
        long enqueued = 0;
        long skipped = 0;
        for (MaintenanceAction action : actions) {
            MaintenanceActionEmbeddingJobStore.EnqueueOutcome outcome = lifecycle.enqueueWhenEnabled(action);
            switch (outcome) {
                case ENQUEUED -> enqueued++;
                case ALREADY_PRESENT -> alreadyPresent++;
                case SKIPPED_BLANK -> skipped++;
            }
        }
        UUID cursor = actions.isEmpty() ? run.cursor() : actions.getLast().getId();
        return backfillStore.recordBatch(runId, new MaintenanceActionEmbeddingBackfillStore.BatchProgress(
                cursor,
                actions.size(),
                alreadyPresent,
                enqueued,
                skipped,
                actions.size() < run.batchSize()));
    }

    @Transactional
    public MaintenanceActionEmbeddingBackfillStore.BackfillRun pause(UUID runId) {
        requireEnabled();
        return backfillStore.transition(runId, MaintenanceActionBackfillStatus.PAUSED);
    }

    @Transactional
    public MaintenanceActionEmbeddingBackfillStore.BackfillRun resume(UUID runId) {
        requireEnabled();
        return backfillStore.transition(runId, MaintenanceActionBackfillStatus.RUNNING);
    }

    @Transactional
    public MaintenanceActionEmbeddingBackfillStore.BackfillRun cancel(UUID runId) {
        requireEnabled();
        return backfillStore.transition(runId, MaintenanceActionBackfillStatus.CANCELLED);
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || !properties.isBackfillEnabled()) {
            throw new IllegalStateException("Maintenance Action embedding backfill is disabled");
        }
        properties.validateExternalGates();
    }
}

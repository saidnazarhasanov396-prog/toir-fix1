package com.toir.service.maintenanceembedding;

import java.time.Instant;
import java.util.UUID;

/** Durable keyset-run persistence boundary, independent of vector storage. */
public interface MaintenanceActionEmbeddingBackfillStore {

    BackfillRun start(StartCommand command);

    BackfillRun find(UUID runId);

    BackfillRun transition(UUID runId, MaintenanceActionBackfillStatus requestedStatus);

    BackfillRun lockForBatch(UUID runId);

    BackfillRun recordBatch(UUID runId, BatchProgress progress);

    record StartCommand(
            String idempotencyKey,
            String modelName,
            String modelRevision,
            int dimension,
            String sourceSchemaVersion,
            int batchSize,
            UUID requestedBy
    ) {
    }

    record BackfillRun(
            UUID id,
            MaintenanceActionBackfillStatus status,
            UUID cursor,
            long scanned,
            long alreadyPresent,
            long enqueued,
            long skipped,
            int batchSize,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record BatchProgress(
            UUID cursor,
            long scanned,
            long alreadyPresent,
            long enqueued,
            long skipped,
            boolean scanCompleted
    ) {
    }
}

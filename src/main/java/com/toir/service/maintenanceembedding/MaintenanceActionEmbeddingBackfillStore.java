package com.toir.service.maintenanceembedding;

import java.time.Instant;
import java.util.UUID;

/** Durable-run persistence boundary; no implementation is supplied before the vector/schema gate is cleared. */
public interface MaintenanceActionEmbeddingBackfillStore {

    BackfillRun start(StartCommand command);

    BackfillRun find(UUID runId);

    BackfillRun transition(UUID runId, MaintenanceActionBackfillStatus requestedStatus);

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
            long ready,
            long retrying,
            long terminalFailed,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}

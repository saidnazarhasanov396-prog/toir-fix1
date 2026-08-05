package com.toir.service.maintenanceembedding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence port only; its pgvector/JDBC implementation is externally gated. */
public interface MaintenanceActionEmbeddingJobStore {

    EnqueueOutcome enqueueCurrent(EnqueueCommand command);

    List<ClaimedJob> claimEligible(int maximumJobs, String leaseOwner, Instant leaseUntil);

    enum EnqueueOutcome {
        ENQUEUED,
        ALREADY_PRESENT,
        SKIPPED_BLANK
    }

    record EnqueueCommand(
            UUID maintenanceActionId,
            String modelName,
            String modelRevision,
            int dimension,
            String sourceSchemaVersion,
            String sourceTextHash,
            String normalizedSourceText,
            int maximumAttempts
    ) {
    }

    record ClaimedJob(
            UUID id,
            UUID maintenanceActionId,
            String normalizedSourceText,
            String sourceTextHash,
            UUID leaseToken
    ) {
    }
}

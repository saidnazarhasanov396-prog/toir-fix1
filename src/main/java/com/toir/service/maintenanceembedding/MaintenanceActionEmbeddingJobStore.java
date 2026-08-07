package com.toir.service.maintenanceembedding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable orchestration port. It deliberately has no operation that can mark a job READY. */
public interface MaintenanceActionEmbeddingJobStore {

    EnqueueOutcome enqueueCurrent(EnqueueCommand command);

    List<ClaimedJob> claimEligible(int maximumJobs, String leaseOwner, Instant leaseUntil);

    RetryOutcome recordRetry(UUID jobId, UUID leaseToken, String safeErrorCode, Instant nextAttemptAt);

    boolean markTerminalFailure(UUID jobId, UUID leaseToken, String safeErrorCode);

    boolean persistVectorAndMarkReady(UUID jobId, UUID leaseToken, List<? extends Number> vector);

    void markCurrentStale(UUID maintenanceActionId);

    enum EnqueueOutcome {
        ENQUEUED,
        ALREADY_PRESENT,
        SKIPPED_BLANK
    }

    enum RetryOutcome {
        RETRY_SCHEDULED,
        TERMINAL_FAILURE,
        FENCE_REJECTED
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
            String modelName,
            String modelRevision,
            int dimension,
            String sourceSchemaVersion,
            int attemptCount,
            int maximumAttempts,
            UUID leaseToken
    ) {
    }
}

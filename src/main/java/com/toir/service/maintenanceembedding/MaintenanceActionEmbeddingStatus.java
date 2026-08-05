package com.toir.service.maintenanceembedding;

/** Contract-independent state model; persistence is blocked until the pgvector prerequisite is confirmed. */
public enum MaintenanceActionEmbeddingStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    READY,
    FAILED,
    STALE,
    SKIPPED
}

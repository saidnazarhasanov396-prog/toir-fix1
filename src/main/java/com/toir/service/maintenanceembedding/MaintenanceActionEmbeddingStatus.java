package com.toir.service.maintenanceembedding;

/** Durable lifecycle state; READY is published only by atomic pgvector persistence. */
public enum MaintenanceActionEmbeddingStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    READY,
    FAILED,
    STALE,
    SKIPPED
}

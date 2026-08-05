package com.toir.service.maintenanceembedding;

import com.toir.entity.maintenance.MaintenanceAction;

/**
 * Transactional boundary for the future durable store. There is deliberately no no-op implementation: core Action
 * writes must not pretend to enqueue work until the pgvector-backed schema and store exist.
 */
public interface MaintenanceActionEmbeddingLifecyclePort {

    void actionCreated(MaintenanceAction action);

    void actionUpdated(MaintenanceAction action, String previousSourceTextHash);

    void actionDeleted(MaintenanceAction action);
}

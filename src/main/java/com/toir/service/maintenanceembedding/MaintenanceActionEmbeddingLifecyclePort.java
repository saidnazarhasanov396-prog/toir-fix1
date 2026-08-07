package com.toir.service.maintenanceembedding;

import com.toir.entity.maintenance.MaintenanceAction;

/**
 * Transactional boundary between Maintenance Action writes and durable representation work.
 * Implementations must never call the external embedding service from these methods.
 */
public interface MaintenanceActionEmbeddingLifecyclePort {

    void actionCreated(MaintenanceAction action);

    void actionUpdated(MaintenanceAction action);

    void actionDeleted(MaintenanceAction action);
}

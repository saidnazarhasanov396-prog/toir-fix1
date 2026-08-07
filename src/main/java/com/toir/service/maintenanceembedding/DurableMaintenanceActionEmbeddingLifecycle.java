package com.toir.service.maintenanceembedding;

import com.toir.config.MaintenanceActionSemanticSearchProperties;
import com.toir.entity.maintenance.MaintenanceAction;
import org.springframework.stereotype.Service;

@Service
public class DurableMaintenanceActionEmbeddingLifecycle implements MaintenanceActionEmbeddingLifecyclePort {

    private final MaintenanceActionEmbeddingJobStore jobStore;
    private final MaintenanceActionEmbeddingTextBuilder textBuilder;
    private final MaintenanceActionSemanticSearchProperties properties;

    public DurableMaintenanceActionEmbeddingLifecycle(
            MaintenanceActionEmbeddingJobStore jobStore,
            MaintenanceActionEmbeddingTextBuilder textBuilder,
            MaintenanceActionSemanticSearchProperties properties
    ) {
        this.jobStore = jobStore;
        this.textBuilder = textBuilder;
        this.properties = properties;
    }

    @Override
    public void actionCreated(MaintenanceAction action) {
        enqueueWhenEnabled(action);
    }

    @Override
    public void actionUpdated(MaintenanceAction action) {
        enqueueWhenEnabled(action);
    }

    @Override
    public void actionDeleted(MaintenanceAction action) {
        if (properties.isEnabled()) {
            jobStore.markCurrentStale(action.getId());
        }
    }

    MaintenanceActionEmbeddingJobStore.EnqueueOutcome enqueueWhenEnabled(MaintenanceAction action) {
        if (!properties.isEnabled()) {
            return MaintenanceActionEmbeddingJobStore.EnqueueOutcome.ALREADY_PRESENT;
        }
        MaintenanceActionEmbeddingTextBuilder.SourceText source = textBuilder.build(action);
        return jobStore.enqueueCurrent(new MaintenanceActionEmbeddingJobStore.EnqueueCommand(
                action.getId(),
                properties.getModelName(),
                properties.getModelRevision(),
                properties.getDimension(),
                MaintenanceActionEmbeddingTextBuilder.SOURCE_SCHEMA_VERSION,
                source.sourceTextHash(),
                source.normalizedText(),
                properties.getRetryCount() + 1));
    }
}

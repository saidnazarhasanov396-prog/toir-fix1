package com.toir.service.equipmentlifecycleexport;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
@ConditionalOnProperty(prefix = "toir.ai.equipment-lifecycle.export", name = "enabled", havingValue = "true")
@Slf4j
public class EquipmentLifecycleExportDispatcher {
    private final Executor executor;
    private final EquipmentLifecycleExportWorker worker;
    private final String workerId = "instance-" + UUID.randomUUID();

    public EquipmentLifecycleExportDispatcher(
            @Qualifier("equipmentLifecycleExportExecutor") Executor executor,
            EquipmentLifecycleExportWorker worker
    ) {
        this.executor = executor;
        this.worker = worker;
    }

    public void dispatchAfterCommit(UUID jobId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(jobId);
                }
            });
            return;
        }
        dispatch(jobId);
    }

    public void dispatch(UUID jobId) {
        try {
            executor.execute(() -> worker.process(jobId, workerId));
        } catch (RejectedExecutionException exception) {
            log.warn("Equipment lifecycle export queue is full; job remains manually resumable: jobId={}", jobId);
        }
    }
}

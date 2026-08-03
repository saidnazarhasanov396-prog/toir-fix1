package com.toir.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogWriteScheduler {

    private final AuditLogWriter writer;

    public void schedule(AuditLogWriteCommand command) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        writer.persist(command);
                    } catch (RuntimeException exception) {
                        log.error("Audit log persistence failed after business transaction commit: module={}, entityType={}, entityId={}",
                                command.module(), command.entityType(), command.entityId(), exception);
                    }
                }
            });
            return;
        }

        writer.persist(command);
    }
}

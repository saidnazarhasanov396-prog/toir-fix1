package com.toir.audit;

import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuditLogWriteSchedulerTest {

    private final AuditLogWriter writer = mock(AuditLogWriter.class);
    private final AuditLogWriteScheduler scheduler = new AuditLogWriteScheduler(writer);

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void writesImmediatelyWhenNoTransactionIsActive() {
        AuditLogWriteCommand command = command();

        scheduler.schedule(command);

        verify(writer).persist(command);
    }

    @Test
    void defersWriteUntilActiveTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        AuditLogWriteCommand command = command();

        scheduler.schedule(command);

        verifyNoInteractions(writer);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(writer).persist(command);
    }

    @Test
    void skipsWriteWhenActiveTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        AuditLogWriteCommand command = command();

        scheduler.schedule(command);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(writer);
    }

    private AuditLogWriteCommand command() {
        return new AuditLogWriteCommand(
                UUID.randomUUID(),
                AuditModule.MATERIAL,
                "materials",
                UUID.randomUUID().toString(),
                AuditAction.UPDATE,
                "updated",
                "127.0.0.1",
                "JUnit",
                "{}",
                null,
                null,
                "updated",
                "TEST",
                "PATCH",
                "/api/v1/materials",
                "req-1"
        );
    }
}

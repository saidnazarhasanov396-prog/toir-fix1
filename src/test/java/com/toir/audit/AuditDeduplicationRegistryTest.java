package com.toir.audit;

import com.toir.enums.AuditAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDeduplicationRegistryTest {

    private final AuditDeduplicationRegistry registry = new AuditDeduplicationRegistry();

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        registry.clear();
    }

    @Test
    void deduplicatesByChangedFingerprintNotOnlyEntityAndAction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThat(registry.markIfFirst("materials", "1", AuditAction.UPDATE, "name")).isTrue();
        assertThat(registry.markIfFirst("materials", "1", AuditAction.UPDATE, "name")).isFalse();
        assertThat(registry.markIfFirst("materials", "1", AuditAction.UPDATE, "unit")).isTrue();
    }

    @Test
    void doesNotRetainDeduplicationKeysOutsideTransaction() {
        assertThat(registry.markIfFirst("materials", "1", AuditAction.UPDATE, "name")).isTrue();
        assertThat(registry.markIfFirst("materials", "1", AuditAction.UPDATE, "name")).isTrue();
    }
}

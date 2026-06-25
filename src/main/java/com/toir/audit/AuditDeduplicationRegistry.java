package com.toir.audit;

import com.toir.enums.AuditAction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.Set;

@Component
public class AuditDeduplicationRegistry {

    private static final ThreadLocal<Set<String>> KEYS = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Boolean> REGISTERED_CLEANUP = ThreadLocal.withInitial(() -> false);

    public boolean markIfFirst(String entityType, String entityId, AuditAction action) {
        registerTransactionCleanupIfNeeded();
        return KEYS.get().add(entityType + "|" + entityId + "|" + action.name());
    }

    public void clear() {
        KEYS.remove();
        REGISTERED_CLEANUP.remove();
    }

    private void registerTransactionCleanupIfNeeded() {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || REGISTERED_CLEANUP.get()) {
            return;
        }
        REGISTERED_CLEANUP.set(true);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                clear();
            }
        });
    }
}

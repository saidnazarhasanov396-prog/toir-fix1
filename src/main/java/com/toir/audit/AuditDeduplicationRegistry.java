package com.toir.audit;

import com.toir.enums.AuditAction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

@Component
public class AuditDeduplicationRegistry {

    private static final ThreadLocal<Set<String>> KEYS = ThreadLocal.withInitial(HashSet::new);
    private static final ThreadLocal<Boolean> REGISTERED_CLEANUP = ThreadLocal.withInitial(() -> false);

    public boolean markIfFirst(String entityType, String entityId, AuditAction action) {
        return markIfFirst(entityType, entityId, action, "");
    }

    public boolean markIfFirst(String entityType, String entityId, AuditAction action, String changedFingerprint) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return true;
        }
        registerTransactionCleanupIfNeeded();
        return KEYS.get().add(entityType + "|" + entityId + "|" + action.name() + "|" + hash(changedFingerprint));
    }

    public void clear() {
        KEYS.remove();
        REGISTERED_CLEANUP.remove();
    }

    private void registerTransactionCleanupIfNeeded() {
        if (REGISTERED_CLEANUP.get()) {
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

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return value == null ? "" : value;
        }
    }
}

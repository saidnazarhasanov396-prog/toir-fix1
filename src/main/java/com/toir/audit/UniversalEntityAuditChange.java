package com.toir.audit;

import com.toir.enums.AuditAction;

import java.util.List;
import java.util.Map;

public record UniversalEntityAuditChange(
        Class<?> entityClass,
        String entityId,
        AuditAction action,
        Map<String, Object> previousSnapshot,
        Map<String, Object> currentSnapshot,
        List<String> changedProperties
) {
}

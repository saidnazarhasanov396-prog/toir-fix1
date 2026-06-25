package com.toir.audit;

import com.toir.entity.AuditLog;
import com.toir.enums.AuditModule;
import jakarta.persistence.Table;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UniversalAuditEntityResolver {

    public boolean isAuditable(Class<?> entityClass) {
        return entityClass != null
                && !AuditLog.class.equals(entityClass)
                && !entityClass.isAnnotationPresent(AuditExcluded.class);
    }

    public String entityType(Class<?> entityClass) {
        AuditedResource auditedResource = entityClass.getAnnotation(AuditedResource.class);
        if (auditedResource != null && !auditedResource.entityType().isBlank()) {
            return auditedResource.entityType();
        }
        Table table = entityClass.getAnnotation(Table.class);
        if (table != null && table.name() != null && !table.name().isBlank()) {
            return table.name();
        }
        return toSnakeCase(entityClass.getSimpleName());
    }

    public AuditModule module(Class<?> entityClass) {
        AuditedResource auditedResource = entityClass.getAnnotation(AuditedResource.class);
        if (auditedResource != null && auditedResource.module().length > 0) {
            return auditedResource.module()[0];
        }
        String directName = toSnakeCase(entityClass.getSimpleName()).toUpperCase(Locale.ROOT);
        try {
            return AuditModule.valueOf(directName);
        } catch (IllegalArgumentException ignored) {
            return packageFallback(entityClass);
        }
    }

    private AuditModule packageFallback(Class<?> entityClass) {
        String packageName = entityClass.getPackageName();
        if (packageName.contains(".equipment")) {
            return AuditModule.EQUIPMENT;
        }
        if (packageName.contains(".warehouse")) {
            return AuditModule.WAREHOUSE;
        }
        if (packageName.contains(".maintenance")) {
            return AuditModule.MAINTENANCE;
        }
        if (packageName.contains(".repair")) {
            return AuditModule.REPAIR_REQUEST;
        }
        if (packageName.contains(".defects")) {
            return AuditModule.DEFECT;
        }
        if (packageName.contains(".contractors")) {
            return AuditModule.CONTRACTORS;
        }
        if (packageName.contains(".projects")) {
            return AuditModule.PROJECTS;
        }
        if (packageName.contains(".users")) {
            return AuditModule.USERS;
        }
        return AuditModule.OTHER;
    }

    public Set<String> redactedFields(Class<?> entityClass) {
        AuditedResource auditedResource = entityClass.getAnnotation(AuditedResource.class);
        if (auditedResource == null) {
            return Set.of();
        }
        return Arrays.stream(auditedResource.redactedFields())
                .filter(field -> field != null && !field.isBlank())
                .collect(Collectors.toSet());
    }

    public Set<String> ignoredFields(Class<?> entityClass) {
        AuditedResource auditedResource = entityClass.getAnnotation(AuditedResource.class);
        if (auditedResource == null) {
            return Set.of();
        }
        return Arrays.stream(auditedResource.ignoredFields())
                .filter(field -> field != null && !field.isBlank())
                .collect(Collectors.toSet());
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }
}

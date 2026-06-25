package com.toir.audit;

import com.toir.enums.AuditAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UniversalAuditChangeFactory {

    private static final String SOFT_DELETE_FIELD = "isDeleted";
    private static final List<String> IGNORED_UPDATE_FIELDS = List.of(
            "createdAt",
            "updatedAt",
            "createdById",
            "updatedById",
            "createdBy",
            "updatedBy"
    );

    private final UniversalAuditEntityResolver entityResolver;

    public Optional<UniversalEntityAuditChange> fromInsert(
            Class<?> entityClass,
            Object entityId,
            String[] propertyNames,
            Object[] state
    ) {
        Map<String, Object> current = snapshot(propertyNames, state, true, entityResolver.ignoredFields(entityClass));
        current.putIfAbsent("id", stringify(entityId));
        return Optional.of(new UniversalEntityAuditChange(
                entityClass,
                stringify(entityId),
                AuditAction.CREATE,
                Map.of(),
                current,
                new ArrayList<>(current.keySet())
        ));
    }

    public Optional<UniversalEntityAuditChange> fromUpdate(
            Class<?> entityClass,
            Object entityId,
            String[] propertyNames,
            Object[] previousState,
            Object[] currentState
    ) {
        Map<String, Object> previous = new LinkedHashMap<>();
        Map<String, Object> current = new LinkedHashMap<>();
        List<String> changed = new ArrayList<>();
        Set<String> ignoredFields = entityResolver.ignoredFields(entityClass);
        boolean softDeleted = false;

        for (int i = 0; i < propertyNames.length; i++) {
            String property = propertyNames[i];
            Object oldValue = valueAt(previousState, i);
            Object newValue = valueAt(currentState, i);
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            if (SOFT_DELETE_FIELD.equals(property)
                    && Boolean.FALSE.equals(oldValue)
                    && Boolean.TRUE.equals(newValue)) {
                softDeleted = true;
            }
            if (ignoredFields.contains(property)) {
                continue;
            }
            if (isIgnoredUpdateField(property) && !SOFT_DELETE_FIELD.equals(property)) {
                continue;
            }
            changed.add(property);
            previous.put(property, oldValue);
            current.put(property, newValue);
        }

        if (changed.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new UniversalEntityAuditChange(
                entityClass,
                stringify(entityId),
                softDeleted ? AuditAction.DELETE : AuditAction.UPDATE,
                previous,
                current,
                changed
        ));
    }

    public Optional<UniversalEntityAuditChange> fromDelete(
            Class<?> entityClass,
            Object entityId,
            String[] propertyNames,
            Object[] deletedState
    ) {
        Map<String, Object> previous = snapshot(propertyNames, deletedState, true, entityResolver.ignoredFields(entityClass));
        previous.putIfAbsent("id", stringify(entityId));
        return Optional.of(new UniversalEntityAuditChange(
                entityClass,
                stringify(entityId),
                AuditAction.DELETE,
                previous,
                Map.of(),
                new ArrayList<>(previous.keySet())
        ));
    }

    private Map<String, Object> snapshot(String[] propertyNames, Object[] state, boolean includeMetadata, Set<String> ignoredFields) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (propertyNames == null || state == null) {
            return snapshot;
        }
        for (int i = 0; i < propertyNames.length; i++) {
            String property = propertyNames[i];
            if (ignoredFields.contains(property)) {
                continue;
            }
            if (!includeMetadata && isIgnoredUpdateField(property)) {
                continue;
            }
            snapshot.put(property, valueAt(state, i));
        }
        return snapshot;
    }

    private Object valueAt(Object[] state, int index) {
        return state == null || index >= state.length ? null : state[index];
    }

    private boolean isIgnoredUpdateField(String property) {
        return IGNORED_UPDATE_FIELDS.contains(property);
    }

    private String stringify(Object value) {
        return value == null ? null : value.toString();
    }
}

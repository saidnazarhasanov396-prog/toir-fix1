package com.toir.util;

import com.toir.entity.BaseEntity;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class UpdatedAtSorter {

    private UpdatedAtSorter() {
    }

    public static <T> List<T> descending(Collection<T> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .sorted(UpdatedAtSorter::compareUpdatedAtDesc)
                .toList();
    }

    public static <T> Comparator<T> descendingComparator() {
        return UpdatedAtSorter::compareUpdatedAtDesc;
    }

    private static int compareUpdatedAtDesc(Object first, Object second) {
        Instant firstUpdatedAt = updatedAt(first);
        Instant secondUpdatedAt = updatedAt(second);
        if (firstUpdatedAt == null && secondUpdatedAt == null) {
            return 0;
        }
        if (firstUpdatedAt == null) {
            return 1;
        }
        if (secondUpdatedAt == null) {
            return -1;
        }
        return secondUpdatedAt.compareTo(firstUpdatedAt);
    }

    private static Instant updatedAt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BaseEntity baseEntity) {
            return baseEntity.getUpdatedAt();
        }
        try {
            Method getter = value.getClass().getMethod("getUpdatedAt");
            Object result = getter.invoke(value);
            return result instanceof Instant instant ? instant : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}

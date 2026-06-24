package com.toir.util;

import org.springframework.data.domain.Sort;

import java.util.Map;

public final class SortUtils {

    private SortUtils() {
    }

    public static Sort sort(String sortBy, String sortDir, Map<String, String> allowedFields, Sort defaultSort) {
        String field = field(sortBy, allowedFields);
        if (field == null) {
            return defaultSort;
        }
        Sort.Direction direction = direction(sortDir, Sort.Direction.ASC);
        return Sort.by(direction, field);
    }

    public static Sort sort(String sortBy,
                            String sortDir,
                            Map<String, String> allowedFields,
                            String defaultField,
                            Sort.Direction defaultDirection) {
        Sort defaultSort = Sort.by(defaultDirection, defaultField);
        return sort(sortBy, sortDir, allowedFields, defaultSort);
    }

    public static String field(String sortBy, Map<String, String> allowedFields) {
        if (sortBy == null || sortBy.isBlank() || allowedFields == null) {
            return null;
        }
        return allowedFields.get(sortBy.trim());
    }

    public static Sort.Direction direction(String sortDir, Sort.Direction defaultDirection) {
        if (sortDir == null || sortDir.isBlank()) {
            return defaultDirection;
        }
        return "desc".equalsIgnoreCase(sortDir.trim()) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}

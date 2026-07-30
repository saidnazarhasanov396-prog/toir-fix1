package com.toir.enums.sparepartlifecycle;

public enum SparePartLifecycleView {
    INSTALLED,
    ATTENTION,
    HISTORY;

    public static SparePartLifecycleView from(String value) {
        if (value == null || value.isBlank()) {
            return INSTALLED;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw com.toir.exception.RestException.badRequest(
                    "view must be installed, attention, or history");
        }
    }
}

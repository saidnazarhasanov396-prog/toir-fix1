package com.toir.config;

import java.util.List;

public record PaginatedResponse<T>(List<T> items, Meta meta) {
    public record Meta(int page, int pageSize, int total) {}

    public static <T> PaginatedResponse<T> of(List<T> items) {
        int size = items != null ? items.size() : 0;
        return new PaginatedResponse<>(items != null ? items : List.of(), new Meta(0, size, size));
    }
}

package com.toir.config;

import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

public record PaginatedResponse<T>(List<T> items, Meta meta) {

    public record Meta(int page, int pageSize, long total) {
    }

    public static <T> PaginatedResponse<T> from(Page<T> page, int publicPage, int requestedPageSize) {
        int safePage = Math.max(1, publicPage);
        int safePageSize = Math.max(1, requestedPageSize);
        return new PaginatedResponse<>(
                page.getContent(),
                new Meta(safePage, safePageSize, page.getTotalElements()));
    }

    public static <T> PaginatedResponse<T> of(List<T> items) {
        List<T> safeItems = items == null ? Collections.emptyList() : items;
        int size = safeItems.size();
        return new PaginatedResponse<>(safeItems, new Meta(1, size, size));
    }
}

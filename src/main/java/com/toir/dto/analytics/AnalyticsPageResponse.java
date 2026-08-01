package com.toir.dto.analytics;

import org.springframework.data.domain.Page;

import java.util.List;

public record AnalyticsPageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        AnalyticsContextDto analyticsContext
) {
    public static <T> AnalyticsPageResponse<T> from(Page<T> page, AnalyticsContextDto context) {
        return new AnalyticsPageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast(), context);
    }
}

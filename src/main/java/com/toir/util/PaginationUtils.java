package com.toir.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

public final class PaginationUtils {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 500;

    private PaginationUtils() {
    }

    public static PageRequest pageRequest(int page, int pageSize) {
        return PageRequest.of(Math.max(page, 0), clampPageSize(pageSize));
    }

    // Use only for JPQL/derived queries. Native SQL queries with their own ORDER BY
    // must use pageRequest(), otherwise Spring appends Java property names to SQL.
    public static PageRequest updatedAtDescPageRequest(int page, int pageSize) {
        return PageRequest.of(
                Math.max(page, 0),
                clampPageSize(pageSize),
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );
    }

    public static <T> Page<T> page(List<T> content, int page, int pageSize, long total) {
        return new PageImpl<>(content != null ? content : List.of(), updatedAtDescPageRequest(page, pageSize), total);
    }

    public static int offset(Pageable pageable) {
        return Math.toIntExact(pageable.getOffset());
    }

    public static int pageSizeFromList(int requestedPageSize, int contentSize) {
        if (requestedPageSize > 0) {
            return clampPageSize(requestedPageSize);
        }
        return contentSize > 0 ? clampPageSize(contentSize) : DEFAULT_PAGE_SIZE;
    }

    private static int clampPageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
    }
}

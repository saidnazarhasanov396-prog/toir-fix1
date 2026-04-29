package com.toir.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PaginationUtils {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 500;

    private PaginationUtils() {
    }

    public static PageRequest pageRequest(int page, int pageSize) {
        return PageRequest.of(Math.max(page, 0), clampPageSize(pageSize));
    }

    public static <T> Page<T> page(List<T> content, int page, int pageSize, long total) {
        return new PageImpl<>(content != null ? content : List.of(), pageRequest(page, pageSize), total);
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

    public static <T> Map<String, Object> pageResponse(List<T> items, int page, int pageSize) {
        return pageResponse(items, page, pageSize, Map.of());
    }

    public static <T> Map<String, Object> pageResponse(List<T> items,
                                                       int page,
                                                       int pageSize,
                                                       Map<String, Object> extras) {
        List<T> safeItems = items != null ? items : List.of();
        Pageable pageable = pageRequest(page, pageSize);
        int fromIndex = Math.min(offset(pageable), safeItems.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), safeItems.size());
        return pageResponse(
                safeItems.subList(fromIndex, toIndex),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                safeItems.size(),
                extras
        );
    }

    public static <T> Map<String, Object> pageResponse(List<T> content,
                                                       int page,
                                                       int pageSize,
                                                       long totalElements,
                                                       Map<String, Object> extras) {
        List<T> safeContent = content != null ? content : List.of();
        Pageable pageable = pageRequest(page, pageSize);
        Map<String, Object> sort = emptySort();
        long offset = pageable.getOffset();
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / (double) pageable.getPageSize());

        Map<String, Object> pageableMap = new LinkedHashMap<>();
        pageableMap.put("pageNumber", pageable.getPageNumber());
        pageableMap.put("pageSize", pageable.getPageSize());
        pageableMap.put("sort", sort);
        pageableMap.put("offset", offset);
        pageableMap.put("paged", true);
        pageableMap.put("unpaged", false);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", safeContent);
        response.put("pageable", pageableMap);
        response.put("last", totalPages == 0 || pageable.getPageNumber() >= totalPages - 1);
        response.put("totalElements", totalElements);
        response.put("totalPages", totalPages);
        response.put("first", pageable.getPageNumber() == 0);
        response.put("size", pageable.getPageSize());
        response.put("number", pageable.getPageNumber());
        response.put("sort", sort);
        response.put("numberOfElements", safeContent.size());
        response.put("empty", safeContent.isEmpty());
        if (extras != null) {
            extras.forEach(response::put);
        }
        return response;
    }

    private static int clampPageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
    }

    private static Map<String, Object> emptySort() {
        Map<String, Object> sort = new LinkedHashMap<>();
        sort.put("sorted", false);
        sort.put("empty", true);
        sort.put("unsorted", true);
        return sort;
    }
}

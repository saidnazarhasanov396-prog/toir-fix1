package com.toir.dto.common;

import com.toir.util.PaginationUtils;
import org.springframework.data.domain.Pageable;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        PageableDto pageable,
        boolean last,
        long totalElements,
        int totalPages,
        boolean first,
        int size,
        int number,
        SortDto sort,
        int numberOfElements,
        boolean empty
) {
    public record PageableDto(
            int pageNumber,
            int pageSize,
            SortDto sort,
            long offset,
            boolean paged,
            boolean unpaged
    ) {
    }

    public record SortDto(boolean sorted, boolean empty, boolean unsorted) {
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize) {
        List<T> safeItems = items != null ? items : List.of();
        Pageable pageable = PaginationUtils.updatedAtDescPageRequest(page, pageSize);
        int fromIndex = Math.min(PaginationUtils.offset(pageable), safeItems.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), safeItems.size());
        List<T> content = safeItems.subList(fromIndex, toIndex);
        long totalElements = safeItems.size();
        int totalPages = totalPages(totalElements, pageable.getPageSize());
        SortDto sort = updatedAtDescSort();
        return new PageResponse<>(
                content,
                new PageableDto(pageable.getPageNumber(), pageable.getPageSize(), sort, pageable.getOffset(), true, false),
                isLast(pageable.getPageNumber(), totalPages),
                totalElements,
                totalPages,
                pageable.getPageNumber() == 0,
                pageable.getPageSize(),
                pageable.getPageNumber(),
                sort,
                content.size(),
                content.isEmpty()
        );
    }

    static int totalPages(long totalElements, int pageSize) {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / (double) pageSize);
    }

    static boolean isLast(int pageNumber, int totalPages) {
        return totalPages == 0 || pageNumber >= totalPages - 1;
    }

    static SortDto emptySort() {
        return new SortDto(false, true, true);
    }

    static SortDto updatedAtDescSort() {
        return new SortDto(true, false, false);
    }
}

package com.toir.dto.common;

import com.toir.util.PaginationUtils;
import org.springframework.data.domain.Pageable;

import java.util.List;

public record PageResponseWithSummary<T, S>(
        List<T> content,
        PageResponse.PageableDto pageable,
        boolean last,
        long totalElements,
        int totalPages,
        boolean first,
        int size,
        int number,
        PageResponse.SortDto sort,
        int numberOfElements,
        boolean empty,
        S summary
) {
    public static <T, S> PageResponseWithSummary<T, S> of(List<T> items, int page, int pageSize, S summary) {
        List<T> safeItems = items != null ? items : List.of();
        Pageable pageable = PaginationUtils.pageRequest(page, pageSize);
        int fromIndex = Math.min(PaginationUtils.offset(pageable), safeItems.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), safeItems.size());
        List<T> content = safeItems.subList(fromIndex, toIndex);
        long totalElements = safeItems.size();
        int totalPages = PageResponse.totalPages(totalElements, pageable.getPageSize());
        PageResponse.SortDto sort = PageResponse.emptySort();
        return new PageResponseWithSummary<>(
                content,
                new PageResponse.PageableDto(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        sort,
                        pageable.getOffset(),
                        true,
                        false
                ),
                PageResponse.isLast(pageable.getPageNumber(), totalPages),
                totalElements,
                totalPages,
                pageable.getPageNumber() == 0,
                pageable.getPageSize(),
                pageable.getPageNumber(),
                sort,
                content.size(),
                content.isEmpty(),
                summary
        );
    }
}

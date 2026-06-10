package com.toir.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationUtilsTest {

    @Test
    void pageRequestWithSortDescending() {
        Sort sort = Sort.by("updatedAt").descending();
        PageRequest req = PaginationUtils.pageRequest(0, 20, sort);

        assertThat(req.getSort().getOrderFor("updatedAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(req.getPageNumber()).isEqualTo(0);
        assertThat(req.getPageSize()).isEqualTo(20);
    }

    @Test
    void pageRequestWithSortAscending() {
        Sort sort = Sort.by("plannedStart").ascending();
        PageRequest req = PaginationUtils.pageRequest(1, 10, sort);

        assertThat(req.getSort().getOrderFor("plannedStart").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(req.getPageNumber()).isEqualTo(1);
    }

    @Test
    void pageRequestWithSortClampsPageSize() {
        Sort sort = Sort.by("updatedAt").descending();
        PageRequest req = PaginationUtils.pageRequest(0, 9999, sort);

        assertThat(req.getPageSize()).isEqualTo(500);
    }

    @Test
    void pageRequestWithSortNormalizesNegativePage() {
        Sort sort = Sort.by("updatedAt").descending();
        PageRequest req = PaginationUtils.pageRequest(-1, 20, sort);

        assertThat(req.getPageNumber()).isEqualTo(0);
    }
}

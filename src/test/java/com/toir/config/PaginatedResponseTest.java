package com.toir.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaginatedResponseTest {

    @Test
    void fromSpringPageKeepsPublicOneBasedPageNumber() {
        PaginatedResponse<String> response = PaginatedResponse.from(
                new PageImpl<>(List.of("A", "B"), PageRequest.of(1, 2), 5),
                2,
                2);

        assertThat(response.items()).containsExactly("A", "B");
        assertThat(response.meta().page()).isEqualTo(2);
        assertThat(response.meta().pageSize()).isEqualTo(2);
        assertThat(response.meta().total()).isEqualTo(5);
    }

    @Test
    void ofListReturnsSinglePageResponse() {
        PaginatedResponse<String> response = PaginatedResponse.of(List.of("A"));

        assertThat(response.items()).containsExactly("A");
        assertThat(response.meta().page()).isEqualTo(1);
        assertThat(response.meta().pageSize()).isEqualTo(1);
        assertThat(response.meta().total()).isEqualTo(1);
    }
}

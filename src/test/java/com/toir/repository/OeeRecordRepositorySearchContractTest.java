package com.toir.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OeeRecordRepositorySearchContractTest {

    @Test
    void searchShouldUseDynamicSpecificationInsteadOfNullableStaticQuery() throws Exception {
        var searchMethod = OeeRecordRepository.class.getMethod(
                "search",
                UUID.class,
                String.class,
                UUID.class,
                UUID.class,
                Instant.class,
                Instant.class
        );

        assertThat(searchMethod.getAnnotation(Query.class))
                .as("Nullable filters must be omitted from generated SQL instead of rendered as '? is null'")
                .isNull();
        assertThat(JpaSpecificationExecutor.class).isAssignableFrom(OeeRecordRepository.class);
    }
}

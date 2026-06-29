package com.toir.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseStockBalanceRepositoryQueryContractTest {

    @Test
    void searchCastsNullableTextFiltersBeforeLowercaseComparison() {
        String query = findSearchQuery().toLowerCase();

        assertThat(query).contains("cast(:lotnumber as string) is null");
        assertThat(query).contains("lower(cast(:lotnumber as string))");
        assertThat(query).contains("cast(:serialnumber as string) is null");
        assertThat(query).contains("lower(cast(:serialnumber as string))");
        assertThat(query).doesNotContain("lower(:lotnumber)");
        assertThat(query).doesNotContain("lower(:serialnumber)");
    }

    private String findSearchQuery() {
        Method method = Arrays.stream(WarehouseStockBalanceRepository.class.getMethods())
                .filter(m -> m.getName().equals("search"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        return query.value();
    }
}

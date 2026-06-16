package com.toir.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SparePartRepositoryQueryContractTest {

    @Test
    void typeCountQueryCountsOnlyActiveItemsOfRequestedKind() {
        Method method = Arrays.stream(SparePartRepository.class.getMethods())
                .filter(m -> m.getName().equals("countActiveByTypeIdsAndKind"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("sp.isDeleted = false");
        assertThat(query.value()).contains("sp.kind = :kind");
        assertThat(query.value()).contains("sp.type.id in :typeIds");
        assertThat(query.value()).contains("group by sp.type.id");
    }
}

package com.toir.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseEquipmentItemRepositoryQueryContractTest {

    @Test
    void findActiveByEquipmentIdQueryFiltersOnlyActiveAndNonDeletedRows() {
        Method method = Arrays.stream(WarehouseEquipmentItemRepository.class.getMethods())
                .filter(m -> m.getName().equals("findActiveByEquipmentId"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("active = true");
        assertThat(query.value()).contains("is_deleted = false");
    }
}

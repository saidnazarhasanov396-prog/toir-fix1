package com.toir.repository.equipment;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentRepositoryQueryContractTest {

    @Test
    void availableForReplacementQueryRequiresActiveNonDeletedWarehouseEquipmentItems() {
        Method method = Arrays.stream(EquipmentRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchAvailableForReplacement"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.value()).contains("wei.active = true");
        assertThat(query.value()).contains("wei.isDeleted = false");
    }
}

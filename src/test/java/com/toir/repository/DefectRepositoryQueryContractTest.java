package com.toir.repository;

import com.toir.repository.defects.DefectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DefectRepositoryQueryContractTest {

    @Test
    void findAllByRepairRequestIdQueryMustFilterNonDeletedRowsAndSortByNewest() {
        Method method = Arrays.stream(DefectRepository.class.getMethods())
                .filter(m -> m.getName().equals("findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();

        String sql = query.value().toLowerCase();
        assertThat(sql).contains("repair_request_id");
        assertThat(sql).contains("is_deleted = false");
        assertThat(sql).contains("order by updated_at desc");
    }
}

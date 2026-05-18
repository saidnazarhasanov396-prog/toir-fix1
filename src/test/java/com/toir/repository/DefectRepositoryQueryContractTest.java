package com.toir.repository;

import com.toir.repository.defects.DefectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DefectRepositoryQueryContractTest {

    @Test
    void queryIncludesRepairRequestIdFilter() {
        Query query = queryOf("searchPaginated");

        String sql = query.value().toLowerCase();
        String countSql = query.countQuery().toLowerCase();
        assertThat(sql).contains("repair_request_id");
        assertThat(countSql).contains("repair_request_id");
        assertThat(sql).contains(":repairrequestid");
        assertThat(countSql).contains(":repairrequestid");
    }

    @Test
    void queryUsesRepairRequestIdColumnAndNotRemovedWorkOrderPath() {
        Query query = queryOf("searchPaginated");

        String sql = query.value().toLowerCase();
        String countSql = query.countQuery().toLowerCase();
        assertThat(sql).contains("d.repair_request_id");
        assertThat(countSql).contains("d.repair_request_id");
        assertThat(sql).doesNotContain("work_order_id");
        assertThat(countSql).doesNotContain("work_order_id");
    }

    @Test
    void queryFiltersIsDeletedFalse() {
        Query query = queryOf("searchPaginated");

        String sql = query.value().toLowerCase();
        String countSql = query.countQuery().toLowerCase();
        assertThat(sql).contains("is_deleted = false");
        assertThat(countSql).contains("is_deleted = false");
    }

    @Test
    void findAllByRepairRequestIdQueryMustFilterNonDeletedRowsAndSortByNewest() {
        Query query = queryOf("findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc");

        String sql = query.value().toLowerCase();
        assertThat(sql).contains("repair_request_id");
        assertThat(sql).contains("is_deleted = false");
        assertThat(sql).contains("order by updated_at desc");
    }

    private Query queryOf(String methodName) {
        Method method = Arrays.stream(DefectRepository.class.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        return query;
    }
}

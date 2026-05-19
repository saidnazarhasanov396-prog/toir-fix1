package com.toir.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderRepositoryQueryContractTest {

    @Test
    void findAllByRepairRequestIdQueryMustFilterNonDeletedRowsAndSortByNewest() {
        Method method = Arrays.stream(WorkOrderRepository.class.getMethods())
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

    @Test
    void findAllByDefectIdQueryMustFilterNonDeletedRowsAndSortByNewest() {
        Method method = Arrays.stream(WorkOrderRepository.class.getMethods())
                .filter(m -> m.getName().equals("findAllByDefectIdAndIsDeletedFalseOrderByUpdatedAtDesc"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();

        String sql = query.value().toLowerCase();
        assertThat(sql).contains("defect_id");
        assertThat(sql).contains("is_deleted = false");
        assertThat(sql).contains("order by updated_at desc");
    }

    @Test
    void searchPaginatedQueryMustTreatBlankSearchAsNoFilter() {
        Method method = Arrays.stream(WorkOrderRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchPaginated"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();

        String sql = query.value().toLowerCase();
        String countSql = query.countQuery().toLowerCase();

        assertThat(sql).contains("nullif(trim(cast(:search as varchar)), '') is null");
        assertThat(countSql).contains("nullif(trim(cast(:search as varchar)), '') is null");
    }

    @Test
    void searchPaginatedQueryMustProjectDefectIdSafely() {
        Method method = Arrays.stream(WorkOrderRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchPaginated"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();

        String sql = query.value().toLowerCase();
        assertThat(sql).contains("to_jsonb(w)->>'defect_id'");
        assertThat(sql).contains("as defect_id");
    }
}

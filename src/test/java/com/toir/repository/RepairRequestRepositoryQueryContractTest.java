package com.toir.repository;

import com.toir.repository.repair.RepairRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RepairRequestRepositoryQueryContractTest {

    @Test
    void queryIncludesEquipmentIdFilter() {
        Query query = queryOf("searchPaginated");

        String sql = query.value().toLowerCase();
        String countSql = query.countQuery().toLowerCase();
        assertThat(sql).contains("equipment_id");
        assertThat(countSql).contains("equipment_id");
        assertThat(sql).contains(":equipmentid");
        assertThat(countSql).contains(":equipmentid");
    }

    @Test
    void queryIncludesStatusFilter() {
        Query query = queryOf("searchPaginated");

        String sql = query.value().toLowerCase();
        String countSql = query.countQuery().toLowerCase();
        assertThat(sql).contains("r.status");
        assertThat(countSql).contains("r.status");
        assertThat(sql).contains(":status");
        assertThat(countSql).contains(":status");
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
    void queryCombinesFiltersWithCorrectAndOrPrecedence() {
        Query query = queryOf("searchPaginated");

        String sql = query.value().toLowerCase();
        String countSql = query.countQuery().toLowerCase();

        assertThat(sql).contains("and (cast(:status as text) is null or r.status = cast(:status as text))");
        assertThat(sql).contains("and (cast(:departmentid as uuid) is null or r.department_id = cast(:departmentid as uuid))");
        assertThat(sql).contains("and (cast(:equipmentid as uuid) is null or r.equipment_id = cast(:equipmentid as uuid))");
        assertThat(sql).contains("and (cast(:search as varchar) is null or (");

        assertThat(countSql).contains("and (cast(:status as text) is null or r.status = cast(:status as text))");
        assertThat(countSql).contains("and (cast(:departmentid as uuid) is null or r.department_id = cast(:departmentid as uuid))");
        assertThat(countSql).contains("and (cast(:equipmentid as uuid) is null or r.equipment_id = cast(:equipmentid as uuid))");
        assertThat(countSql).contains("and (cast(:search as varchar) is null or (");
    }

    private Query queryOf(String methodName) {
        Method method = Arrays.stream(RepairRequestRepository.class.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        return query;
    }
}

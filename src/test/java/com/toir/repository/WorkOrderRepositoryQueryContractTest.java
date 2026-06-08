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
                .filter(m -> m.getName().equals("searchPaginated") && m.getAnnotation(Query.class) != null)
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
                .filter(m -> m.getName().equals("searchPaginated") && m.getAnnotation(Query.class) != null)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();

        String sql = query.value().toLowerCase();
        assertThat(sql).contains("to_jsonb(w)->>'defect_id'");
        assertThat(sql).contains("as defect_id");
    }

    @Test
    void searchPaginatedQueryMustProjectBrigadeMemberIdForEntityMapping() {
        Method method = Arrays.stream(WorkOrderRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchPaginated") && m.getAnnotation(Query.class) != null)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();

        String sql = query.value().toLowerCase();
        assertThat(sql).contains("brigade_member_id");
        assertThat(sql).contains("as brigade_member_id");
    }

    @Test
    void searchPaginatedQueryMustFilterByPlannedDateRange() {
        Method method = Arrays.stream(WorkOrderRepository.class.getMethods())
                .filter(m -> m.getName().equals("searchPaginated") && m.getAnnotation(Query.class) != null)
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();

        String sql = query.value().toLowerCase();
        String countSql = query.countQuery().toLowerCase();

        assertThat(sql).contains("coalesce(w.end_planned_at, w.start_planned_at)");
        assertThat(sql).contains(":plannedfrom");
        assertThat(sql).contains(":plannedto");
        assertThat(countSql).contains("coalesce(w.end_planned_at, w.start_planned_at)");
        assertThat(countSql).contains(":plannedfrom");
        assertThat(countSql).contains(":plannedto");
    }

    @Test
    void calendarBucketQueriesMustUsePlannedDateAndExistingFilters() {
        Method monthMethod = Arrays.stream(WorkOrderRepository.class.getMethods())
                .filter(m -> m.getName().equals("getWorkOrderCalendarMonthBuckets"))
                .findFirst()
                .orElseThrow();
        Method dayMethod = Arrays.stream(WorkOrderRepository.class.getMethods())
                .filter(m -> m.getName().equals("getWorkOrderCalendarDayBuckets"))
                .findFirst()
                .orElseThrow();

        Query monthQuery = monthMethod.getAnnotation(Query.class);
        Query dayQuery = dayMethod.getAnnotation(Query.class);
        assertThat(monthQuery).isNotNull();
        assertThat(dayQuery).isNotNull();

        String monthSql = monthQuery.value().toLowerCase();
        String daySql = dayQuery.value().toLowerCase();

        assertThat(monthSql).contains("timezone('asia/tashkent'");
        assertThat(monthSql).contains("coalesce(w.end_planned_at, w.start_planned_at)");
        assertThat(monthSql).contains("w.is_deleted = false");
        assertThat(monthSql).contains(":departmentid");
        assertThat(monthSql).contains(":equipmentid");
        assertThat(monthSql).contains(":status");
        assertThat(monthSql).contains(":search");

        assertThat(daySql).contains("timezone('asia/tashkent'");
        assertThat(daySql).contains("coalesce(w.end_planned_at, w.start_planned_at)");
        assertThat(daySql).contains("w.is_deleted = false");
        assertThat(daySql).contains(":departmentid");
        assertThat(daySql).contains(":equipmentid");
        assertThat(daySql).contains(":status");
        assertThat(daySql).contains(":search");
    }
}

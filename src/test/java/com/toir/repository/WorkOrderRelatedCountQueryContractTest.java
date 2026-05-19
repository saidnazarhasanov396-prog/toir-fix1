package com.toir.repository;

import com.toir.repository.repair.RepairMaterialUsageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderRelatedCountQueryContractTest {

    @Test
    void workExecutionCountQueryGroupsByWorkOrderAndFiltersDeletedRows() {
        Method method = Arrays.stream(WorkExecutionRepository.class.getMethods())
                .filter(m -> m.getName().equals("countByWorkOrderIds"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();

        String sql = query.value().toLowerCase();
        assertThat(sql).contains("count(");
        assertThat(sql).contains("e.isdeleted = false");
        assertThat(sql).contains("e.workorderid in :workorderids");
        assertThat(sql).contains("group by e.workorderid");
    }

    @Test
    void repairMaterialUsageCountQueryGroupsByWorkOrderAndFiltersDeletedRows() {
        Method method = Arrays.stream(RepairMaterialUsageRepository.class.getMethods())
                .filter(m -> m.getName().equals("countByWorkOrderIds"))
                .findFirst()
                .orElseThrow();

        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();

        String sql = query.value().toLowerCase();
        assertThat(sql).contains("count(");
        assertThat(sql).contains("u.isdeleted = false");
        assertThat(sql).contains("u.workorderid in :workorderids");
        assertThat(sql).contains("group by u.workorderid");
    }
}

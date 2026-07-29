package com.toir.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.entity.PprTask;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class PprTaskRepositoryMaterializationContractTest {

    @Test
    void materializationLookupIsPlanBoundBatchedAndIncludesSoftDeletedTasks()
            throws Exception {
        Method method = PprTaskRepository.class.getDeclaredMethod(
                "findAllByPlanIdAndSourceCalculationItemIdIn",
                UUID.class,
                Collection.class);

        assertThat(method.getReturnType()).isEqualTo(List.class);
        assertThat(method.getGenericReturnType().getTypeName())
                .isEqualTo("java.util.List<com.toir.entity.PprTask>");
        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        assertThat(query.nativeQuery()).isFalse();
        assertThat(query.value())
                .contains("task.plan.id = :planId")
                .contains("task.sourceCalculationItemId in :sourceCalculationItemIds")
                .doesNotContain("task.isDeleted");
        assertThat(PprTask.class.getDeclaredField("sourceCalculationItemId").getType())
                .isEqualTo(UUID.class);
    }
}

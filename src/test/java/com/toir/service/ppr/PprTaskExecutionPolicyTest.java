package com.toir.service.ppr;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toir.entity.PprTask;
import com.toir.exception.RestException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PprTaskExecutionPolicyTest {

    @Test
    void materializedAnnualTaskCannotBeCompletedDirectly() {
        PprTask task = new PprTask();
        task.setSourceVariantItemId(UUID.randomUUID());

        assertThatThrownBy(() -> PprTaskExecutionPolicy.requireDirectCompletionAllowed(task))
                .isInstanceOfSatisfying(RestException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo("PPR_TASK_REQUIRES_WORK_ORDER"));
    }
}

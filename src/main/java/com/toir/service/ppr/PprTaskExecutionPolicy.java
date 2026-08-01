package com.toir.service.ppr;

import com.toir.entity.PprTask;
import com.toir.exception.RestException;

public final class PprTaskExecutionPolicy {

    private PprTaskExecutionPolicy() {
    }

    public static void requireDirectCompletionAllowed(PprTask task) {
        if (task.getSourceCalculationItemId() != null || task.getSourceVariantItemId() != null) {
            throw RestException.conflict(
                    "Annual PPR tasks must be completed through the linked work order, acceptance and act",
                    "PPR_TASK_REQUIRES_WORK_ORDER");
        }
    }
}

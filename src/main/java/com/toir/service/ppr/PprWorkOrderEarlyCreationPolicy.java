package com.toir.service.ppr;

import com.toir.entity.PprTask;
import com.toir.exception.RestException;
import java.time.LocalDateTime;

public final class PprWorkOrderEarlyCreationPolicy {

    private PprWorkOrderEarlyCreationPolicy() {
    }

    public static LocalDateTime generationAt(PprTask task) {
        return task.getScheduledStart() == null
                ? null
                : task.getScheduledStart().minusDays(task.getWorkOrderLeadDays());
    }

    public static void requireDue(PprTask task, LocalDateTime now) {
        LocalDateTime generationAt = generationAt(task);
        if (generationAt != null && now.isBefore(generationAt)) {
            throw RestException.conflict(
                    "Work order will be available at " + generationAt,
                    "PPR_WORK_ORDER_NOT_DUE");
        }
    }
}

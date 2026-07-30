package com.toir.service.ppr;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PprWorkOrderEligibilityServiceTest {

    private final PprWorkOrderEligibilityService service = new PprWorkOrderEligibilityService();
    private final LocalDateTime scheduledStart = LocalDateTime.of(2027, 3, 10, 9, 0);

    @Test
    void becomesDueExactlyAtConfiguredLeadTimeBoundary() {
        PprTask task = task(PlanStatus.APPROVED, PprTaskStatus.APPROVED, 7);

        assertThat(service.evaluate(task, scheduledStart.minusDays(7).minusNanos(1), false).eligible()).isFalse();
        assertThat(service.evaluate(task, scheduledStart.minusDays(7), false).eligible()).isTrue();
    }

    @Test
    void supportsZeroAndMaximumLeadDays() {
        PprTask sameDay = task(PlanStatus.APPROVED, PprTaskStatus.APPROVED, 0);
        PprTask yearBefore = task(PlanStatus.APPROVED, PprTaskStatus.APPROVED, 365);

        assertThat(service.evaluate(sameDay, scheduledStart, false).eligible()).isTrue();
        assertThat(service.evaluate(yearBefore, scheduledStart.minusDays(365), false).eligible()).isTrue();
    }

    @Test
    void rejectsCancelledPlanAndExistingWorkOrder() {
        PprTask cancelled = task(PlanStatus.CANCELLED, PprTaskStatus.APPROVED, 7);
        PprTask approved = task(PlanStatus.APPROVED, PprTaskStatus.APPROVED, 7);

        assertThat(service.evaluate(cancelled, scheduledStart, false).reason()).isEqualTo("PLAN_STATUS_NOT_EXECUTABLE");
        assertThat(service.evaluate(approved, scheduledStart, true).reason()).isEqualTo("WORK_ORDER_ALREADY_EXISTS");
    }

    @Test
    void rejectsNonApprovedTaskAndMissingSchedule() {
        PprTask planned = task(PlanStatus.APPROVED, PprTaskStatus.PLANNED, 7);
        PprTask missingSchedule = task(PlanStatus.APPROVED, PprTaskStatus.APPROVED, 7);
        missingSchedule.setScheduledStart(null);

        assertThat(service.evaluate(planned, scheduledStart, false).reason()).isEqualTo("TASK_STATUS_NOT_APPROVED");
        assertThat(service.evaluate(missingSchedule, scheduledStart, false).reason()).isEqualTo("TASK_SCHEDULE_MISSING");
    }

    private PprTask task(PlanStatus planStatus, PprTaskStatus taskStatus, int leadDays) {
        PprPlan plan = new PprPlan();
        plan.setStatus(planStatus);
        PprTask task = new PprTask();
        task.setPlan(plan);
        task.setStatus(taskStatus);
        task.setScheduledStart(scheduledStart);
        task.setWorkOrderLeadDays(leadDays);
        return task;
    }
}

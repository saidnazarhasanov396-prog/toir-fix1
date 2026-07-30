package com.toir.service.pprcalendar;

import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.TaskMaterializationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PprOperationalCalendarPolicyTest {

    private final PprOperationalCalendarPolicy policy =
            new PprOperationalCalendarPolicy();

    @Test
    void includesOnlyMaterializedMaintenanceSchedulesInOperationalStates() {
        assertThat(policy.includes(materializedSchedule(PlanStatus.APPROVED))).isTrue();
        assertThat(policy.includes(materializedSchedule(PlanStatus.IN_PROGRESS))).isTrue();
        assertThat(policy.includes(materializedSchedule(PlanStatus.CLOSED))).isTrue();
        assertThat(policy.includes(materializedSchedule(PlanStatus.CANCELLED))).isTrue();

        PprPlan manual = materializedSchedule(PlanStatus.APPROVED);
        manual.setOrigin(PprPlanOrigin.MANUAL);
        assertThat(policy.includes(manual)).isFalse();

        PprPlan calculated = materializedSchedule(PlanStatus.CALCULATED);
        assertThat(policy.includes(calculated)).isFalse();
    }

    @Test
    void excludesPlansWithoutConsistentMaterializationMetadata() {
        PprPlan notMaterialized = materializedSchedule(PlanStatus.APPROVED);
        notMaterialized.setTaskMaterializationStatus(
                TaskMaterializationStatus.NOT_MATERIALIZED);
        assertThat(policy.includes(notMaterialized)).isFalse();

        PprPlan mismatchedRevision = materializedSchedule(PlanStatus.APPROVED);
        mismatchedRevision.setMaterializedRevision(2L);
        assertThat(policy.includes(mismatchedRevision)).isFalse();

        PprPlan missingCount = materializedSchedule(PlanStatus.APPROVED);
        missingCount.setMaterializedTaskCount(null);
        assertThat(policy.includes(missingCount)).isFalse();
    }

    @Test
    void includesOnlySnapshotLinkedTasksFromEligiblePlans() {
        PprPlan plan = materializedSchedule(PlanStatus.APPROVED);
        PprTask linked = task(plan, UUID.randomUUID());
        assertThat(policy.includes(linked)).isTrue();

        PprTask manual = task(plan, null);
        assertThat(policy.includes(manual)).isFalse();

        PprTask deleted = task(plan, UUID.randomUUID());
        deleted.setDeleted(true);
        assertThat(policy.includes(deleted)).isFalse();
    }

    private static PprPlan materializedSchedule(PlanStatus status) {
        PprPlan plan = new PprPlan();
        plan.setOrigin(PprPlanOrigin.MAINTENANCE_SCHEDULE);
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.MATERIALIZED);
        plan.setStatus(status);
        plan.setCalculationRevision(1L);
        plan.setApprovedRevision(1L);
        plan.setMaterializedRevision(1L);
        plan.setMaterializedTaskCount(1);
        return plan;
    }

    private static PprTask task(PprPlan plan, UUID sourceCalculationItemId) {
        PprTask task = new PprTask();
        task.setPlan(plan);
        task.setSourceCalculationItemId(sourceCalculationItemId);
        return task;
    }
}

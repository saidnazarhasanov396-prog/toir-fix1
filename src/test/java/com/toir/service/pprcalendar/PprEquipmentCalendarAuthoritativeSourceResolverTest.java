package com.toir.service.pprcalendar;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;
import com.toir.entity.PprPlan;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.exception.RestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PprEquipmentCalendarAuthoritativeSourceResolverTest {

    private final PprEquipmentCalendarAuthoritativeSourceResolver resolver =
            new PprEquipmentCalendarAuthoritativeSourceResolver();

    @Test
    void selectsCurrentCalculationSnapshotOnlyForNonMaterializedApprovalFirstPlans() {
        PprPlan plan = new PprPlan();
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_MATERIALIZED);
        plan.setStatus(PlanStatus.CALCULATED);
        plan.setCalculationRevision(7L);

        PprEquipmentCalendarSourceSelection selection = resolver.resolve(plan);

        assertThat(selection.authoritativeSource())
                .isEqualTo(PprEquipmentCalendarAuthoritativeSource.CALCULATION_SNAPSHOT);
        assertThat(selection.calculationRevision()).isEqualTo(7L);
    }

    @Test
    void selectsExactMaterializedRevisionForApprovalFirstTasks() {
        PprPlan legacy = new PprPlan();
        legacy.setMaterializationMode(MaterializationMode.LEGACY_MATERIALIZED);
        legacy.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_APPLICABLE);
        legacy.setStatus(PlanStatus.APPROVED);
        legacy.setCalculationRevision(3L);

        PprPlan materialized = new PprPlan();
        materialized.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        materialized.setTaskMaterializationStatus(TaskMaterializationStatus.MATERIALIZED);
        materialized.setStatus(PlanStatus.APPROVED);
        materialized.setCalculationRevision(4L);
        materialized.setApprovedRevision(4L);
        materialized.setMaterializedRevision(4L);
        materialized.setCalculationContentHash("a".repeat(64));
        materialized.setApprovedContentHash("a".repeat(64));
        materialized.setCalculationContentHashVersion(1);
        materialized.setApprovedContentHashVersion(1);

        assertThat(resolver.resolve(legacy))
                .isEqualTo(new PprEquipmentCalendarSourceSelection(
                        PprEquipmentCalendarAuthoritativeSource.PPR_TASK, null));
        assertThat(resolver.resolve(materialized))
                .isEqualTo(new PprEquipmentCalendarSourceSelection(
                        PprEquipmentCalendarAuthoritativeSource.PPR_TASK, 4L));
    }

    @Test
    void failsClosedWhenApprovalFirstPlanHasNoCurrentCalculationRevision() {
        PprPlan plan = new PprPlan();
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_MATERIALIZED);
        plan.setStatus(PlanStatus.CALCULATED);
        plan.setCalculationRevision(null);

        assertThatThrownBy(() -> resolver.resolve(plan))
                .isInstanceOfSatisfying(RestException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo("PPR_CALENDAR_SOURCE_INTEGRITY"));
    }
}

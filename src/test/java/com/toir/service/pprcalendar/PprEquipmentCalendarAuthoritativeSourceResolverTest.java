package com.toir.service.pprcalendar;

import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;
import com.toir.entity.PprPlan;
import com.toir.enums.MaterializationMode;
import com.toir.enums.TaskMaterializationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PprEquipmentCalendarAuthoritativeSourceResolverTest {

    private final PprEquipmentCalendarAuthoritativeSourceResolver resolver =
            new PprEquipmentCalendarAuthoritativeSourceResolver();

    @Test
    void selectsCurrentCalculationSnapshotOnlyForNonMaterializedApprovalFirstPlans() {
        PprPlan plan = new PprPlan();
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_MATERIALIZED);
        plan.setCalculationRevision(7L);

        PprEquipmentCalendarSourceSelection selection = resolver.resolve(plan);

        assertThat(selection.authoritativeSource())
                .isEqualTo(PprEquipmentCalendarAuthoritativeSource.CALCULATION_SNAPSHOT);
        assertThat(selection.calculationRevision()).isEqualTo(7L);
    }

    @Test
    void selectsTasksForLegacyOrMaterializedApprovalFirstPlansAndDoesNotExposeRevision() {
        PprPlan legacy = new PprPlan();
        legacy.setMaterializationMode(MaterializationMode.LEGACY_MATERIALIZED);
        legacy.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_APPLICABLE);
        legacy.setCalculationRevision(3L);

        PprPlan materialized = new PprPlan();
        materialized.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        materialized.setTaskMaterializationStatus(TaskMaterializationStatus.MATERIALIZED);
        materialized.setCalculationRevision(4L);

        assertThat(resolver.resolve(legacy))
                .isEqualTo(new PprEquipmentCalendarSourceSelection(
                        PprEquipmentCalendarAuthoritativeSource.PPR_TASK, null));
        assertThat(resolver.resolve(materialized))
                .isEqualTo(new PprEquipmentCalendarSourceSelection(
                        PprEquipmentCalendarAuthoritativeSource.PPR_TASK, null));
    }

    @Test
    void selectsTasksWhenApprovalFirstPlanHasNoCurrentCalculationRevision() {
        PprPlan plan = new PprPlan();
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_MATERIALIZED);
        plan.setCalculationRevision(null);

        assertThat(resolver.resolve(plan))
                .isEqualTo(new PprEquipmentCalendarSourceSelection(
                        PprEquipmentCalendarAuthoritativeSource.PPR_TASK, null));
    }
}

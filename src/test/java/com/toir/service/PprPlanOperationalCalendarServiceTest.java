package com.toir.service;

import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.MaterializationMode;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprPlanOrigin;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.TaskMaterializationStatus;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.service.pprcalendar.PprOperationalCalendarPolicy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PprPlanOperationalCalendarServiceTest {

    @Mock PprPlanRepository planRepository;
    @Mock PprTaskRepository taskRepository;
    @Mock PprTaskQueryService pprTaskQueryService;
    @Mock DepartmentRepository departmentRepository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock EquipmentTypeRepository equipmentTypeRepository;
    @Mock EquipmentMaintenanceRuleRepository equipmentMaintenanceRuleRepository;
    @Mock MaintenanceRegulationRepository maintenanceRegulationRepository;
    @Spy PprOperationalCalendarPolicy operationalCalendarPolicy;

    @InjectMocks PprPlanService service;

    @Test
    void operationalPlanListFiltersBeforePagination() {
        PprPlan eligible = materializedPlan(PlanStatus.APPROVED);
        PprTask generatedTask = task(
                eligible, UUID.randomUUID(), PprTaskStatus.APPROVED);
        task(eligible, null, PprTaskStatus.APPROVED);
        PprPlan manual = materializedPlan(PlanStatus.APPROVED);
        manual.setOrigin(PprPlanOrigin.MANUAL);
        PprPlan stale = materializedPlan(PlanStatus.APPROVED);
        stale.setMaterializedRevision(2L);

        when(planRepository.searchPlansByStatuses(
                        eq(2026), eq(5), eq(null), eq(null), eq(null), anyCollection()))
                .thenReturn(List.of(eligible, manual, stale));

        var result = service.findOperationalCalendarPlans(
                2026, 5, null, null, null, Set.of(), 0, 1, null, "asc");

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting("id").containsExactly(eligible.getId());
        assertThat(result.getContent().getFirst().tasks())
                .extracting("id")
                .containsExactly(generatedTask.getId());
        assertThat(result.getContent().getFirst().taskCount()).isEqualTo(1);
    }

    @Test
    void operationalTaskListKeepsOnlySnapshotLinkedTasksFromEligiblePlans() {
        PprPlan eligiblePlan = materializedPlan(PlanStatus.APPROVED);
        PprTask eligible = task(eligiblePlan, UUID.randomUUID(), PprTaskStatus.APPROVED);
        PprTask manual = task(eligiblePlan, null, PprTaskStatus.APPROVED);
        PprPlan stalePlan = materializedPlan(PlanStatus.APPROVED);
        stalePlan.setTaskMaterializationStatus(TaskMaterializationStatus.NOT_MATERIALIZED);
        PprTask stale = task(stalePlan, UUID.randomUUID(), PprTaskStatus.APPROVED);

        when(pprTaskQueryService.findTasksByStatuses(
                        null,
                        null,
                        EnumSet.allOf(PprTaskStatus.class),
                        false))
                .thenReturn(List.of(eligible, manual, stale));

        var result = service.findOperationalCalendarTasks(
                null, null, Set.of(), false, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting("id").containsExactly(eligible.getId());
    }

    @Test
    void operationalDetailsReturnNotFoundForIneligibleResources() {
        PprPlan manual = materializedPlan(PlanStatus.APPROVED);
        manual.setOrigin(PprPlanOrigin.MANUAL);
        PprTask manualTask = task(manual, null, PprTaskStatus.APPROVED);
        when(planRepository.findByIdAndIsDeletedFalse(manual.getId()))
                .thenReturn(Optional.of(manual));
        when(taskRepository.findByIdAndIsDeletedFalse(manualTask.getId()))
                .thenReturn(Optional.of(manualTask));

        assertThatThrownBy(
                        () -> service.findOperationalCalendarPlanById(manual.getId()))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(
                        () -> service.findOperationalCalendarTaskById(manualTask.getId()))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static PprPlan materializedPlan(PlanStatus status) {
        PprPlan plan = new PprPlan();
        plan.setId(UUID.randomUUID());
        plan.setCode("PPR-" + plan.getId());
        plan.setName("Approved schedule");
        plan.setStatus(status);
        plan.setOrigin(PprPlanOrigin.MAINTENANCE_SCHEDULE);
        plan.setMaterializationMode(MaterializationMode.APPROVAL_FIRST);
        plan.setTaskMaterializationStatus(TaskMaterializationStatus.MATERIALIZED);
        plan.setCalculationRevision(1L);
        plan.setApprovedRevision(1L);
        plan.setMaterializedRevision(1L);
        plan.setMaterializedTaskCount(1);
        plan.setStartDate(LocalDate.of(2026, 1, 1));
        plan.setEndDate(LocalDate.of(2026, 12, 31));
        plan.setTasks(new ArrayList<>());
        plan.setTargets(new ArrayList<>());
        return plan;
    }

    private static PprTask task(
            PprPlan plan,
            UUID sourceCalculationItemId,
            PprTaskStatus status) {
        PprTask task = new PprTask();
        task.setId(UUID.randomUUID());
        task.setPlan(plan);
        task.setCode("TASK-" + task.getId());
        task.setTitle("Maintenance");
        task.setStatus(status);
        task.setSourceCalculationItemId(sourceCalculationItemId);
        plan.getTasks().add(task);
        return task;
    }
}

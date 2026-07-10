package com.toir.repository;

import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.test.RepositorySliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
class PprPlanRepositoryStatsTest {

    @Autowired
    PprPlanRepository repository;

    @Autowired
    PprTaskRepository taskRepository;

    @Autowired
    EquipmentRepository equipmentRepository;

    @Test
    void getStatsCountsOverdueTasksFromStatusAndDueDateOnlyForMatchingDepartment() {
        UUID departmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        PprPlan targetPlan = plan("PPR-STATS-1", departmentId);
        targetPlan.getTasks().add(task(targetPlan, "PPR-TASK-1", PprTaskStatus.OVERDUE, LocalDateTime.now().plusDays(2)));
        targetPlan.getTasks().add(task(targetPlan, "PPR-TASK-2", PprTaskStatus.PLANNED, LocalDateTime.now().minusDays(1)));
        targetPlan.getTasks().add(task(targetPlan, "PPR-TASK-3", PprTaskStatus.APPROVED, LocalDateTime.now().minusHours(2)));
        targetPlan.getTasks().add(task(targetPlan, "PPR-TASK-4", PprTaskStatus.IN_PROGRESS, LocalDateTime.now().minusMinutes(30)));
        targetPlan.getTasks().add(task(targetPlan, "PPR-TASK-5", PprTaskStatus.POSTPONED, LocalDateTime.now().minusDays(1)));
        targetPlan.getTasks().add(task(targetPlan, "PPR-TASK-6", PprTaskStatus.COMPLETED, LocalDateTime.now().minusDays(1)));
        targetPlan.getTasks().add(task(targetPlan, "PPR-TASK-7", PprTaskStatus.CANCELLED, LocalDateTime.now().minusDays(1)));
        repository.saveAndFlush(targetPlan);

        PprPlan otherPlan = plan("PPR-STATS-2", otherDepartmentId);
        otherPlan.getTasks().add(task(otherPlan, "PPR-TASK-8", PprTaskStatus.OVERDUE, LocalDateTime.now().minusDays(1)));
        repository.saveAndFlush(otherPlan);

        PprPlanStatsProjection stats = repository.getStats(null, null, null, departmentId);

        assertThat(stats.getTotalPlans()).isEqualTo(1);
        assertThat(stats.getOverdueTasks()).isEqualTo(4);
    }

    @Test
    void searchTasksScopesByEquipmentDepartmentAndExactStatus() {
        UUID departmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        UUID equipmentId = equipmentRepository.saveAndFlush(equipment("EQ-PPR-1", departmentId)).getId();
        UUID otherEquipmentId = equipmentRepository.saveAndFlush(equipment("EQ-PPR-2", otherDepartmentId)).getId();
        PprPlan targetPlan = plan("PPR-TASK-SCOPE-1", departmentId);
        PprTask completed = task(targetPlan, "PPR-TASK-SCOPE-1", PprTaskStatus.COMPLETED, LocalDateTime.now());
        completed.setEquipmentId(equipmentId);
        targetPlan.getTasks().add(completed);
        PprTask planned = task(targetPlan, "PPR-TASK-SCOPE-2", PprTaskStatus.PLANNED, LocalDateTime.now());
        planned.setEquipmentId(equipmentId);
        targetPlan.getTasks().add(planned);
        PprPlan otherPlan = plan("PPR-TASK-SCOPE-3", otherDepartmentId);
        PprTask otherCompleted = task(otherPlan, "PPR-TASK-SCOPE-4", PprTaskStatus.COMPLETED, LocalDateTime.now());
        otherCompleted.setEquipmentId(otherEquipmentId);
        otherPlan.getTasks().add(otherCompleted);
        repository.saveAndFlush(targetPlan);
        repository.saveAndFlush(otherPlan);

        var page = taskRepository.searchTasks(departmentId, null, PprTaskStatus.COMPLETED, PageRequest.of(0, 10));
        var allScopedTasks = taskRepository.searchTasks(departmentId, null, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getCode()).isEqualTo("PPR-TASK-SCOPE-1");
        assertThat(allScopedTasks).extracting(PprTask::getCode)
                .containsExactlyInAnyOrder("PPR-TASK-SCOPE-1", "PPR-TASK-SCOPE-2");
    }

    @Test
    void searchTasksWithOverduePredicateMatchesDashboardRuleAndKeepsPageTotal() {
        UUID departmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        UUID equipmentId = equipmentRepository.saveAndFlush(equipment("EQ-PPR-OVERDUE-1", departmentId)).getId();
        UUID otherEquipmentId = equipmentRepository.saveAndFlush(equipment("EQ-PPR-OVERDUE-2", otherDepartmentId)).getId();
        LocalDateTime now = LocalDateTime.of(2026, 6, 10, 12, 0);
        PprPlan targetPlan = plan("PPR-OVERDUE-FILTER-1", departmentId);
        PprTask explicitOverdue = task(targetPlan, "PPR-OVERDUE-FILTER-1", PprTaskStatus.OVERDUE, now.plusDays(1));
        explicitOverdue.setEquipmentId(equipmentId);
        targetPlan.getTasks().add(explicitOverdue);
        PprTask plannedPastDue = task(targetPlan, "PPR-OVERDUE-FILTER-2", PprTaskStatus.PLANNED, now.minusMinutes(1));
        plannedPastDue.setEquipmentId(equipmentId);
        targetPlan.getTasks().add(plannedPastDue);
        PprTask approvedPastDue = task(targetPlan, "PPR-OVERDUE-FILTER-3", PprTaskStatus.APPROVED, now.minusHours(1));
        approvedPastDue.setEquipmentId(equipmentId);
        targetPlan.getTasks().add(approvedPastDue);
        PprTask inProgressPastDue = task(targetPlan, "PPR-OVERDUE-FILTER-4", PprTaskStatus.IN_PROGRESS, now.minusDays(1));
        inProgressPastDue.setEquipmentId(equipmentId);
        targetPlan.getTasks().add(inProgressPastDue);
        PprTask dueAtBoundary = task(targetPlan, "PPR-OVERDUE-FILTER-5", PprTaskStatus.PLANNED, now);
        dueAtBoundary.setEquipmentId(equipmentId);
        targetPlan.getTasks().add(dueAtBoundary);
        PprTask completedPastDue = task(targetPlan, "PPR-OVERDUE-FILTER-6", PprTaskStatus.COMPLETED, now.minusDays(1));
        completedPastDue.setEquipmentId(equipmentId);
        targetPlan.getTasks().add(completedPastDue);
        PprTask cancelledPastDue = task(targetPlan, "PPR-OVERDUE-FILTER-7", PprTaskStatus.CANCELLED, now.minusDays(1));
        cancelledPastDue.setEquipmentId(equipmentId);
        targetPlan.getTasks().add(cancelledPastDue);
        PprTask postponedPastDue = task(targetPlan, "PPR-OVERDUE-FILTER-8", PprTaskStatus.POSTPONED, now.minusDays(1));
        postponedPastDue.setEquipmentId(equipmentId);
        targetPlan.getTasks().add(postponedPastDue);
        PprTask softDeletedPastDue = task(targetPlan, "PPR-OVERDUE-FILTER-9", PprTaskStatus.PLANNED, now.minusDays(1));
        softDeletedPastDue.setEquipmentId(equipmentId);
        softDeletedPastDue.setDeleted(true);
        targetPlan.getTasks().add(softDeletedPastDue);
        repository.saveAndFlush(targetPlan);

        PprPlan otherPlan = plan("PPR-OVERDUE-FILTER-10", otherDepartmentId);
        PprTask otherDepartmentPastDue = task(otherPlan, "PPR-OVERDUE-FILTER-11", PprTaskStatus.PLANNED, now.minusDays(1));
        otherDepartmentPastDue.setEquipmentId(otherEquipmentId);
        otherPlan.getTasks().add(otherDepartmentPastDue);
        repository.saveAndFlush(otherPlan);

        var firstPage = taskRepository.searchTasks(
                departmentId,
                null,
                null,
                true,
                now,
                PageRequest.of(0, 2)
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(taskRepository.searchTasks(departmentId, null, null, true, now))
                .extracting(PprTask::getCode)
                .containsExactlyInAnyOrder(
                        "PPR-OVERDUE-FILTER-1",
                        "PPR-OVERDUE-FILTER-2",
                        "PPR-OVERDUE-FILTER-3",
                        "PPR-OVERDUE-FILTER-4"
                );
        assertThat(taskRepository.searchTasks(departmentId, null, PprTaskStatus.PLANNED, true, now))
                .extracting(PprTask::getCode)
                .containsExactly("PPR-OVERDUE-FILTER-2");
    }

    private PprPlan plan(String code, UUID departmentId) {
        PprPlan plan = new PprPlan();
        plan.setCode(code);
        plan.setName(code);
        plan.setDepartmentId(departmentId);
        plan.setStartDate(LocalDate.now().withDayOfMonth(1));
        plan.setEndDate(LocalDate.now().withDayOfMonth(1).plusDays(10));
        plan.setStatus(PlanStatus.APPROVED);
        return plan;
    }

    private PprTask task(PprPlan plan, String code, PprTaskStatus status, LocalDateTime dueDate) {
        PprTask task = new PprTask();
        task.setPlan(plan);
        task.setCode(code);
        task.setTitle(code);
        task.setEquipmentId(UUID.randomUUID());
        task.setScheduledStart(dueDate.minusHours(1));
        task.setScheduledEnd(dueDate);
        task.setDueDate(dueDate);
        task.setStatus(status);
        task.setPlannedLaborHours(1.0);
        return task;
    }

    private Equipment equipment(String code, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setCode(code);
        equipment.setName(code);
        equipment.setInventoryNumber(code + "-INV");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        return equipment;
    }
}

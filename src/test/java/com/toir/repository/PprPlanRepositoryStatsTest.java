package com.toir.repository;

import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.test.RepositorySliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
class PprPlanRepositoryStatsTest {

    @Autowired
    PprPlanRepository repository;

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
}

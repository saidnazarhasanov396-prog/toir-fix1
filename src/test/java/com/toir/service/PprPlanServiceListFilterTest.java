package com.toir.service;

import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.entity.Department;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.PlanStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.PprTaskStatus;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprPlanStatsProjection;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PprPlanServiceListFilterTest {

    @Mock
    PprPlanRepository planRepository;

    @Mock
    PprTaskRepository taskRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    AuditSerializationService auditSerializationService;

    @InjectMocks
    PprPlanService service;

    @Test
    void listWithoutFiltersKeepsOldBehavior() {
        PprPlan plan = plan(2026, 5, UUID.randomUUID());
        when(planRepository.searchPlans(null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(plan), PageRequest.of(0, 20), 1));

        var result = service.findAll(null, null, null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(PprPlanDto::id).containsExactly(plan.getId());
        verify(planRepository).searchPlans(null, null, null, PageRequest.of(0, 20));
    }

    @Test
    void listFiltersByYear() {
        when(planRepository.searchPlans(2026, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.findAll(2026, null, null, 0, 20);

        verify(planRepository).searchPlans(2026, null, null, PageRequest.of(0, 20));
    }

    @Test
    void listFiltersByMonth() {
        when(planRepository.searchPlans(null, 5, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.findAll(null, 5, null, 0, 20);

        verify(planRepository).searchPlans(null, 5, null, PageRequest.of(0, 20));
    }

    @Test
    void listFiltersByYearAndMonth() {
        when(planRepository.searchPlans(2026, 5, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.findAll(2026, 5, null, 0, 20);

        verify(planRepository).searchPlans(2026, 5, null, PageRequest.of(0, 20));
    }

    @Test
    void listFiltersByDepartmentId() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.searchPlans(null, null, departmentId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.findAll(null, null, departmentId, 0, 20);

        verify(planRepository).searchPlans(null, null, departmentId, PageRequest.of(0, 20));
    }

    @Test
    void listFiltersByYearMonthAndDepartmentId() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.searchPlans(2026, 5, departmentId, PageRequest.of(1, 10)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 10), 0));

        service.findAll(2026, 5, departmentId, 1, 10);

        verify(planRepository).searchPlans(2026, 5, departmentId, PageRequest.of(1, 10));
    }

    @Test
    void nonExistingDepartmentIdReturnsEmptyPageFromRepository() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.searchPlans(null, null, departmentId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var result = service.findAll(null, null, departmentId, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void responseContainsDepartmentIdAndDepartmentName() {
        UUID departmentId = UUID.randomUUID();
        PprPlan plan = plan(2026, 5, departmentId);
        Department department = new Department();
        department.setId(departmentId);
        department.setName("Mechanical");

        when(planRepository.searchPlans(null, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(plan), PageRequest.of(0, 20), 1));
        when(departmentRepository.findAllByIdInAndIsDeletedFalse(List.of(departmentId)))
                .thenReturn(List.of(department));

        var result = service.findAll(null, null, null, 0, 20);

        PprPlanDto dto = result.getContent().getFirst();
        assertThat(dto.departmentId()).isEqualTo(departmentId);
        assertThat(dto.departmentName()).isEqualTo("Mechanical");
    }

    @Test
    void statsWithoutFiltersReturnsCountsByStatus() {
        when(planRepository.getStats(null, null, null)).thenReturn(stats(6, 1, 2, 3, 8, 4, 5));

        var result = service.getStats(null, null, null);

        assertThat(result.totalPlans()).isEqualTo(6);
        assertThat(result.draftPlans()).isEqualTo(1);
        assertThat(result.generatedPlans()).isEqualTo(2);
        assertThat(result.approvedPlans()).isEqualTo(3);
        assertThat(result.plannedTasks()).isEqualTo(8);
        assertThat(result.inProgressTasks()).isEqualTo(4);
        assertThat(result.completedTasks()).isEqualTo(5);
        verify(planRepository).getStats(null, null, null);
    }

    @Test
    void statsWithYearMonthAndDepartmentUsesSameFilters() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.getStats(2026, 5, departmentId)).thenReturn(stats(2, 0, 1, 1, 3, 2, 1));

        var result = service.getStats(2026, 5, departmentId);

        assertThat(result.totalPlans()).isEqualTo(2);
        assertThat(result.generatedPlans()).isEqualTo(1);
        assertThat(result.approvedPlans()).isEqualTo(1);
        verify(planRepository).getStats(2026, 5, departmentId);
    }

    @Test
    void statsForNonExistingDepartmentReturnsZeroCounts() {
        UUID departmentId = UUID.randomUUID();
        when(planRepository.getStats(null, null, departmentId)).thenReturn(stats(0, 0, 0, 0, 0, 0, 0));

        var result = service.getStats(null, null, departmentId);

        assertThat(result.totalPlans()).isZero();
        assertThat(result.plannedTasks()).isZero();
        verify(planRepository).getStats(null, null, departmentId);
    }

    @Test
    void findTasksByPlanUsesStableScheduleOrder() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(2026, 5, UUID.randomUUID());
        plan.setId(planId);
        PprTask first = task(plan, LocalDateTime.of(2026, 5, 1, 9, 0));
        PprTask second = task(plan, LocalDateTime.of(2026, 5, 2, 9, 0));
        when(taskRepository.findAllByPlanIdAndIsDeletedFalseOrderByScheduledStartAscIdAsc(planId))
                .thenReturn(List.of(first, second));

        var result = service.findTasksByPlan(planId);

        assertThat(result).extracting("id").containsExactly(first.getId(), second.getId());
        verify(taskRepository).findAllByPlanIdAndIsDeletedFalseOrderByScheduledStartAscIdAsc(planId);
    }

    private PprPlan plan(int year, int month, UUID departmentId) {
        PprPlan plan = new PprPlan();
        plan.setId(UUID.randomUUID());
        plan.setCode("PPR-2026-0001");
        plan.setName("May plan");
        plan.setYear(year);
        plan.setMonth(month);
        plan.setStatus(PlanStatus.DRAFT);
        plan.setDepartmentId(departmentId);
        plan.setCreatedById(UUID.randomUUID());
        plan.setTasks(new ArrayList<>());
        return plan;
    }

    private PprTask task(PprPlan plan, LocalDateTime scheduledStart) {
        PprTask task = new PprTask();
        task.setId(UUID.randomUUID());
        task.setCode("PPR-TASK-" + scheduledStart.toLocalDate());
        task.setPlan(plan);
        task.setRegulationId(UUID.randomUUID());
        task.setEquipmentId(UUID.randomUUID());
        task.setTitle("Task");
        task.setScheduledStart(scheduledStart);
        task.setScheduledEnd(scheduledStart.plusHours(2));
        task.setDueDate(scheduledStart.plusDays(1));
        task.setStatus(PprTaskStatus.PLANNED);
        task.setPriority(PriorityLevel.MEDIUM);
        task.setPlannedLaborHours(2.0);
        return task;
    }

    private PprPlanStatsProjection stats(
            long totalPlans,
            long draftPlans,
            long generatedPlans,
            long approvedPlans,
            long plannedTasks,
            long inProgressTasks,
            long completedTasks
    ) {
        return new PprPlanStatsProjection() {
            @Override
            public Long getTotalPlans() {
                return totalPlans;
            }

            @Override
            public Long getDraftPlans() {
                return draftPlans;
            }

            @Override
            public Long getGeneratedPlans() {
                return generatedPlans;
            }

            @Override
            public Long getApprovedPlans() {
                return approvedPlans;
            }

            @Override
            public Long getPlannedTasks() {
                return plannedTasks;
            }

            @Override
            public Long getInProgressTasks() {
                return inProgressTasks;
            }

            @Override
            public Long getCompletedTasks() {
                return completedTasks;
            }
        };
    }
}

package com.toir.service;

import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.dto.pprplanning.PprTaskRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PprPlanServiceLifecycleTest {

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
    void addTaskSucceedsForDraftAndGeneratedPlans() {
        for (PlanStatus status : new PlanStatus[]{PlanStatus.DRAFT, PlanStatus.GENERATED}) {
            UUID planId = UUID.randomUUID();
            PprPlan plan = plan(planId, status);
            String codePrefix = "PPR-TASK-" + Year.now().getValue() + "-";
            String expectedCode = "PPR-TASK-" + Year.now().getValue() + "-0001";

            when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));
            when(taskRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
            when(taskRepository.existsByCode(expectedCode)).thenReturn(false);
            when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> {
                PprTask task = invocation.getArgument(0);
                task.setId(UUID.randomUUID());
                return task;
            });

            PprTaskDto created = service.addTask(planId, taskRequest());

            assertThat(created.status()).isEqualTo(PprTaskStatus.PLANNED);
        }
    }

    @Test
    void addTaskBlockedForApprovedInProgressClosedAndCancelledPlans() {
        for (PlanStatus status : new PlanStatus[]{
                PlanStatus.APPROVED,
                PlanStatus.IN_PROGRESS,
                PlanStatus.CLOSED,
                PlanStatus.CANCELLED
        }) {
            UUID planId = UUID.randomUUID();
            when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, status)));

            assertThatThrownBy(() -> service.addTask(planId, taskRequest()))
                    .isInstanceOfSatisfying(RestException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).contains("DRAFT or GENERATED");
                    });
        }

        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void approveTaskBlockedWhenParentPlanIsDraftOrGenerated() {
        for (PlanStatus status : new PlanStatus[]{PlanStatus.DRAFT, PlanStatus.GENERATED}) {
            UUID taskId = UUID.randomUUID();
            when(taskRepository.findByIdAndIsDeletedFalse(taskId))
                    .thenReturn(Optional.of(task(taskId, plan(UUID.randomUUID(), status), PprTaskStatus.PLANNED)));

            assertThatThrownBy(() -> service.approveTask(taskId))
                    .isInstanceOfSatisfying(RestException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).contains("parent PPR plan is approved");
                    });
        }

        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void approveTaskSucceedsWhenParentPlanApproved() {
        UUID taskId = UUID.randomUUID();
        PprTask task = task(taskId, plan(UUID.randomUUID(), PlanStatus.APPROVED), PprTaskStatus.PLANNED);

        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PprTaskDto response = service.approveTask(taskId);

        assertThat(response.status()).isEqualTo(PprTaskStatus.APPROVED);
    }

    @Test
    void startTaskBlockedWhenParentPlanDraftOrGenerated() {
        for (PlanStatus status : new PlanStatus[]{PlanStatus.DRAFT, PlanStatus.GENERATED}) {
            UUID taskId = UUID.randomUUID();
            when(taskRepository.findByIdAndIsDeletedFalse(taskId))
                    .thenReturn(Optional.of(task(taskId, plan(UUID.randomUUID(), status), PprTaskStatus.APPROVED)));

            assertThatThrownBy(() -> service.startTask(taskId))
                    .isInstanceOfSatisfying(RestException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getMessage()).isEqualTo("Task can start only after the parent PPR plan is approved");
                    });
        }

        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void startTaskBlockedWhenTaskIsPlanned() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndIsDeletedFalse(taskId))
                .thenReturn(Optional.of(task(taskId, plan(UUID.randomUUID(), PlanStatus.APPROVED), PprTaskStatus.PLANNED)));

        assertThatThrownBy(() -> service.startTask(taskId))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Only APPROVED PPR tasks can be started");
                });

        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void startTaskSucceedsWhenParentPlanApprovedAndTaskApproved() {
        UUID taskId = UUID.randomUUID();
        PprTask task = task(taskId, plan(UUID.randomUUID(), PlanStatus.APPROVED), PprTaskStatus.APPROVED);

        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));
        when(planRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PprTaskDto response = service.startTask(taskId);

        assertThat(response.status()).isEqualTo(PprTaskStatus.IN_PROGRESS);
    }

    @Test
    void startingTaskUpdatesApprovedParentPlanToInProgress() {
        UUID taskId = UUID.randomUUID();
        PprPlan plan = plan(UUID.randomUUID(), PlanStatus.APPROVED);
        PprTask task = task(taskId, plan, PprTaskStatus.APPROVED);

        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));
        when(planRepository.save(any(PprPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.startTask(taskId);

        assertThat(plan.getStatus()).isEqualTo(PlanStatus.IN_PROGRESS);
        verify(planRepository).save(plan);
    }

    private PprPlan plan(UUID id, PlanStatus status) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setStatus(status);
        plan.setTasks(new ArrayList<>());
        return plan;
    }

    private PprTask task(UUID id, PprPlan plan, PprTaskStatus status) {
        PprTask task = new PprTask();
        task.setId(id);
        task.setCode("PPR-TASK-2026-0001");
        task.setPlan(plan);
        task.setRegulationId(UUID.randomUUID());
        task.setEquipmentId(UUID.randomUUID());
        task.setTitle("PPR task");
        task.setScheduledStart(LocalDateTime.of(2026, 6, 1, 9, 0));
        task.setScheduledEnd(LocalDateTime.of(2026, 6, 1, 12, 0));
        task.setDueDate(LocalDateTime.of(2026, 6, 2, 9, 0));
        task.setStatus(status);
        task.setPriority(PriorityLevel.MEDIUM);
        task.setPlannedLaborHours(2.0);
        return task;
    }

    private PprTaskRequest taskRequest() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 0);
        return new PprTaskRequest(
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Manual PPR task",
                null,
                null,
                start,
                start.plusHours(2),
                start.plusDays(1),
                PriorityLevel.MEDIUM,
                2.0
        );
    }
}

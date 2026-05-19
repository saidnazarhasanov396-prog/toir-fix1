package com.toir.service;

import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.dto.pprplanning.PprPlanRequest;
import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.dto.pprplanning.PprTaskRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PprPlanServiceTaskCodePolicyTest {

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
    void createWithoutCodeStillGeneratesCode() {
        int year = Year.now().getValue();
        String codePrefix = "PPR-" + year + "-";
        String expectedCode = "PPR-" + year + "-0001";

        when(planRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(planRepository.existsByCodeAndIsDeletedFalse(expectedCode)).thenReturn(false);
        when(planRepository.save(any(PprPlan.class))).thenAnswer(invocation -> {
            PprPlan plan = invocation.getArgument(0);
            plan.setId(UUID.randomUUID());
            return plan;
        });

        PprPlanDto created = service.create(planRequest());

        assertThat(created.code()).isEqualTo(expectedCode);
        verify(planRepository).maxSequenceByCodePrefix(codePrefix);
    }

    @Test
    void generatedCodeIsUnique() {
        int year = Year.now().getValue();
        String codePrefix = "PPR-" + year + "-";
        String firstCandidate = "PPR-" + year + "-0001";
        String secondCandidate = "PPR-" + year + "-0002";

        when(planRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(planRepository.existsByCodeAndIsDeletedFalse(firstCandidate)).thenReturn(true);
        when(planRepository.existsByCodeAndIsDeletedFalse(secondCandidate)).thenReturn(false);
        when(planRepository.save(any(PprPlan.class))).thenAnswer(invocation -> {
            PprPlan plan = invocation.getArgument(0);
            plan.setId(UUID.randomUUID());
            return plan;
        });

        PprPlanDto created = service.create(planRequest());

        assertThat(created.code()).isEqualTo(secondCandidate);
        verify(planRepository).existsByCodeAndIsDeletedFalse(firstCandidate);
        verify(planRepository).existsByCodeAndIsDeletedFalse(secondCandidate);
    }

    @Test
    void duplicateCodeDoesNotReturn500() {
        int year = Year.now().getValue();
        String codePrefix = "PPR-" + year + "-";
        String firstCandidate = "PPR-" + year + "-0001";
        String secondCandidate = "PPR-" + year + "-0002";

        when(planRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(planRepository.existsByCodeAndIsDeletedFalse(firstCandidate)).thenReturn(false);
        when(planRepository.existsByCodeAndIsDeletedFalse(secondCandidate)).thenReturn(false);
        when(planRepository.save(any(PprPlan.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"ppr_plans_code_key\""))
                .thenAnswer(invocation -> {
                    PprPlan plan = invocation.getArgument(0);
                    plan.setId(UUID.randomUUID());
                    return plan;
                });

        PprPlanDto created = service.create(planRequest());

        assertThat(created.code()).isEqualTo(secondCandidate);
        verify(planRepository, times(2)).save(any(PprPlan.class));
    }

    @Test
    void responseIncludesGeneratedCode() {
        int year = Year.now().getValue();
        String expectedCode = "PPR-" + year + "-0001";

        when(planRepository.maxSequenceByCodePrefix("PPR-" + year + "-")).thenReturn(0L);
        when(planRepository.existsByCodeAndIsDeletedFalse(expectedCode)).thenReturn(false);
        when(planRepository.save(any(PprPlan.class))).thenAnswer(invocation -> {
            PprPlan plan = invocation.getArgument(0);
            plan.setId(UUID.randomUUID());
            return plan;
        });

        PprPlanDto created = service.create(planRequest());

        assertThat(created.code()).isEqualTo(expectedCode);
    }

    @Test
    void createTaskWithoutCodeGeneratesCode() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId);
        int year = Year.now().getValue();
        String codePrefix = "PPR-TASK-" + year + "-";
        String expectedCode = "PPR-TASK-" + year + "-0001";

        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));
        when(taskRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(taskRepository.existsByCode(expectedCode)).thenReturn(false);
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> {
            PprTask task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        PprTaskDto created = service.addTask(planId, request(null));

        assertThat(created.code()).isEqualTo(expectedCode);
        verify(taskRepository).maxSequenceByCodePrefix(codePrefix);
        ArgumentCaptor<PprTask> captor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(expectedCode);
    }

    @Test
    void createTaskWithClientCodeReturns400() {
        assertThatThrownBy(() -> service.addTask(UUID.randomUUID(), request("PPR-TASK-2026-9001")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("code is generated by backend and must not be provided");
                });

        verify(planRepository, never()).findByIdAndIsDeletedFalse(any(UUID.class));
        verify(taskRepository, never()).maxSequenceByCodePrefix(anyString());
        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void updateTaskDoesNotAllowChangingCode() {
        UUID taskId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        String originalCode = "PPR-TASK-2026-0007";
        PprTask existingTask = new PprTask();
        existingTask.setId(taskId);
        existingTask.setCode(originalCode);
        existingTask.setPlan(plan(planId));
        existingTask.setStatus(PprTaskStatus.PLANNED);

        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(existingTask));
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PprTaskDto updated = service.approveTask(taskId);

        assertThat(updated.code()).isEqualTo(originalCode);
        assertThat(updated.status()).isEqualTo(PprTaskStatus.APPROVED);
    }

    @Test
    void generatedTaskCodeIsUnique() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId);
        int year = Year.now().getValue();
        String codePrefix = "PPR-TASK-" + year + "-";
        String firstCandidate = "PPR-TASK-" + year + "-0001";
        String secondCandidate = "PPR-TASK-" + year + "-0002";

        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));
        when(taskRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(taskRepository.existsByCode(firstCandidate)).thenReturn(true);
        when(taskRepository.existsByCode(secondCandidate)).thenReturn(false);
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> {
            PprTask task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        PprTaskDto created = service.addTask(planId, request(null));

        assertThat(created.code()).isEqualTo(secondCandidate);
        verify(taskRepository).existsByCode(firstCandidate);
        verify(taskRepository).existsByCode(secondCandidate);
    }

    @Test
    void duplicateTaskCodeDoesNotReturn500() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId);
        int year = Year.now().getValue();
        String codePrefix = "PPR-TASK-" + year + "-";
        String firstCandidate = "PPR-TASK-" + year + "-0001";
        String secondCandidate = "PPR-TASK-" + year + "-0002";

        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));
        when(taskRepository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(taskRepository.existsByCode(firstCandidate)).thenReturn(false);
        when(taskRepository.existsByCode(secondCandidate)).thenReturn(false);
        when(taskRepository.save(any(PprTask.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"ppr_tasks_code_key\""))
                .thenAnswer(invocation -> {
                    PprTask task = invocation.getArgument(0);
                    task.setId(UUID.randomUUID());
                    return task;
                });

        PprTaskDto created = service.addTask(planId, request(null));

        assertThat(created.code()).isEqualTo(secondCandidate);
        verify(taskRepository, times(2)).save(any(PprTask.class));
    }

    @Test
    void createTaskWithStartDateAndEndDatePersistsAndReturnsDateAliases() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId);
        LocalDate startDate = LocalDate.of(2026, 5, 1);
        LocalDate endDate = LocalDate.of(2026, 5, 10);

        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));
        when(taskRepository.maxSequenceByCodePrefix("PPR-TASK-" + Year.now().getValue() + "-")).thenReturn(0L);
        when(taskRepository.existsByCode("PPR-TASK-" + Year.now().getValue() + "-0001")).thenReturn(false);
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> {
            PprTask task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        PprTaskDto created = service.addTask(planId, requestWithDates(startDate, endDate));

        assertThat(created.startDate()).isEqualTo(startDate);
        assertThat(created.endDate()).isEqualTo(endDate);

        ArgumentCaptor<PprTask> captor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getScheduledStart()).isEqualTo(startDate.atStartOfDay());
        assertThat(captor.getValue().getScheduledEnd()).isEqualTo(endDate.atTime(23, 59, 59));
        assertThat(captor.getValue().getDueDate()).isEqualTo(endDate.atTime(23, 59, 59));
    }

    @Test
    void createTaskRejectsEndDateBeforeStartDate() {
        UUID planId = UUID.randomUUID();
        PprPlan plan = plan(planId);

        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.addTask(
                planId,
                requestWithDates(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1))
        ))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("PPR task end date must not be before start date");
                });

        verify(taskRepository, never()).save(any(PprTask.class));
    }

    @Test
    void startTaskUpdatesSameTaskAndReturnsStatusAndDates() {
        UUID taskId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        PprTask task = new PprTask();
        task.setId(taskId);
        task.setCode("PPR-TASK-2026-0001");
        task.setPlan(plan(planId));
        task.setRegulationId(UUID.randomUUID());
        task.setEquipmentId(UUID.randomUUID());
        task.setTitle("Manual PPR task");
        task.setScheduledStart(LocalDateTime.of(2026, 5, 1, 9, 0));
        task.setScheduledEnd(LocalDateTime.of(2026, 5, 10, 18, 0));
        task.setDueDate(LocalDateTime.of(2026, 5, 10, 18, 0));
        task.setStatus(PprTaskStatus.PLANNED);
        task.setPriority(PriorityLevel.MEDIUM);
        task.setPlannedLaborHours(2.0);

        when(taskRepository.findByIdAndIsDeletedFalse(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(PprTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PprTaskDto result = service.startTask(taskId);

        assertThat(result.id()).isEqualTo(taskId);
        assertThat(result.status()).isEqualTo(PprTaskStatus.IN_PROGRESS);
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 5, 10));

        ArgumentCaptor<PprTask> captor = ArgumentCaptor.forClass(PprTask.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(taskId);
        assertThat(captor.getValue().getStatus()).isEqualTo(PprTaskStatus.IN_PROGRESS);
    }

    private PprPlan plan(UUID id) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setTasks(new ArrayList<>());
        return plan;
    }

    private PprTaskRequest request(String code) {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 0);
        return new PprTaskRequest(
                code,
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

    private PprTaskRequest requestWithDates(LocalDate startDate, LocalDate endDate) {
        return new PprTaskRequest(
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Manual PPR task",
                startDate,
                endDate,
                null,
                null,
                null,
                PriorityLevel.MEDIUM,
                2.0
        );
    }

    private PprPlanRequest planRequest() {
        return new PprPlanRequest(
                "June plan",
                2026,
                6,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Planned maintenance"
        );
    }
}

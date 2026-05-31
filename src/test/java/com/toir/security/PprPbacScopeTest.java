package com.toir.security;

import com.toir.controller.PprPlanController;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.dto.pprplanning.PprPlanStatsResponse;
import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.service.PprGeneratorService;
import com.toir.service.PprPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PprPbacScopeTest {

    @Mock
    PprPlanService service;

    @Mock
    PprGeneratorService generatorService;

    @Mock
    PprPlanRepository planRepository;

    @Mock
    PprTaskRepository taskRepository;

    @Mock
    ScopeAccessService scopeAccessService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PprPlanController(service, generatorService, planRepository, taskRepository, scopeAccessService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listClampsRequestedDepartmentToCurrentUserDepartment() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(currentDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(service.findAllUnpaged(2026, 5, 12, currentDepartmentId))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/ppr-plans")
                        .param("year", "2026")
                        .param("month", "5")
                        .param("day", "12")
                        .param("departmentId", requestedDepartmentId.toString()))
                .andExpect(status().isOk());

        verify(service).findAllUnpaged(2026, 5, 12, currentDepartmentId);
    }

    @Test
    void paginatedListClampsRequestedDepartmentToCurrentUserDepartment() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(currentDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(service.findAll(2026, 5, 12, currentDepartmentId, 0, 20))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/ppr-plans")
                        .param("year", "2026")
                        .param("month", "5")
                        .param("day", "12")
                        .param("departmentId", requestedDepartmentId.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk());

        verify(service).findAll(2026, 5, 12, currentDepartmentId, 0, 20);
    }

    @Test
    void statsWithoutCurrentDepartmentIsDeniedForNonAdmin() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);

        mockMvc.perform(get("/api/v1/ppr-plans/stats"))
                .andExpect(status().isForbidden());

        verify(service, never()).getStats(any(), any(), any(), any());
    }

    @Test
    void adminCanRequestGlobalList() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(service.findAllUnpaged(null, null, null, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/ppr-plans"))
                .andExpect(status().isOk());

        verify(service).findAllUnpaged(null, null, null, null);
    }

    @Test
    void detailAllowsPlanInScope() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, departmentId)));
        when(service.findById(planId)).thenReturn(planDto(planId, departmentId));

        mockMvc.perform(get("/api/v1/ppr-plans/{id}", planId))
                .andExpect(status().isOk());

        verify(scopeAccessService, atLeastOnce()).assertCanAccessDepartment(departmentId);
    }

    @Test
    void detailDeniesPlanOutOfScope() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, departmentId)));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(get("/api/v1/ppr-plans/{id}", planId))
                .andExpect(status().isForbidden());

        verify(service, never()).findById(planId);
    }

    @Test
    void missingPlanStillReturns404() throws Exception {
        UUID planId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/ppr-plans/{id}", planId))
                .andExpect(status().isNotFound());
    }

    @Test
    void createDeniesPlanForOtherDepartment() throws Exception {
        UUID departmentId = UUID.randomUUID();
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType("application/json")
                        .content(planPayload(departmentId)))
                .andExpect(status().isForbidden());

        verify(service, never()).create(any());
    }

    @Test
    void updateChecksExistingPlanAndTargetDepartment() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, departmentId)));
        when(service.update(eq(planId), any())).thenReturn(planDto(planId, departmentId));

        mockMvc.perform(patch("/api/v1/ppr-plans/{id}", planId)
                        .contentType("application/json")
                        .content(planPayload(departmentId)))
                .andExpect(status().isOk());

        verify(scopeAccessService, atLeastOnce()).assertCanAccessDepartment(departmentId);
    }

    @Test
    void deleteDeniesPlanOutOfScope() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, departmentId)));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(delete("/api/v1/ppr-plans/{id}", planId))
                .andExpect(status().isForbidden());

        verify(service, never()).delete(planId);
    }

    @Test
    void generateChecksPlanScopeBeforeDelegating() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, departmentId)));
        when(generatorService.generateForPlan(planId)).thenReturn(new PprGeneratorService.GenerationResult(planId, 1, 0));

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/generate", planId))
                .andExpect(status().isOk());

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
    }

    @Test
    void generateWorkOrdersChecksPlanScopeBeforeDelegating() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, departmentId)));
        when(generatorService.generateWorkOrdersForPlan(planId, createdById))
                .thenReturn(new PprGeneratorService.WorkOrderGenerationResult(planId, 1, 0, List.of(UUID.randomUUID()), List.of()));

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/work-orders/generate", planId)
                        .param("createdById", createdById.toString()))
                .andExpect(status().isOk());

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
        verify(generatorService).generateWorkOrdersForPlan(planId, createdById);
    }

    @Test
    void taskListChecksParentPlanScope() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, departmentId)));
        when(service.findTasksByPlan(planId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/ppr-plans/{id}/tasks", planId))
                .andExpect(status().isOk());

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
    }

    @Test
    void taskCreateDeniesOutOfScopeParentPlan() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, departmentId)));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/tasks", planId)
                        .contentType("application/json")
                        .content(taskPayload()))
                .andExpect(status().isForbidden());

        verify(service, never()).addTask(eq(planId), any());
    }

    @Test
    void taskLifecycleChecksParentPlanScope() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(taskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(task(taskId, plan(planId, departmentId))));
        when(service.startTask(taskId)).thenReturn(taskDto(planId));

        mockMvc.perform(post("/api/v1/ppr-plans/tasks/{taskId}/start", taskId))
                .andExpect(status().isOk());

        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
    }

    @Test
    void taskLifecycleMissingTaskStillReturns404() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/ppr-plans/tasks/{taskId}/start", taskId))
                .andExpect(status().isNotFound());

        verify(service, never()).startTask(taskId);
    }

    @Test
    void taskLifecycleExistingOutOfScopeTaskReturns403() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(taskRepository.findByIdAndIsDeletedFalseWithPlan(taskId)).thenReturn(Optional.of(task(taskId, plan(planId, departmentId))));
        doThrow(new AccessDeniedException("Access denied by data scope"))
                .when(scopeAccessService).assertCanAccessDepartment(departmentId);

        mockMvc.perform(post("/api/v1/ppr-plans/tasks/{taskId}/complete", taskId)
                        .param("actualLaborHours", "1.5"))
                .andExpect(status().isForbidden());

        verify(service, never()).completeTask(eq(taskId), anyDouble());
    }

    private PprPlan plan(UUID id, UUID departmentId) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("May PPR");
        plan.setStartDate(LocalDate.of(2026, 5, 1));
        plan.setEndDate(LocalDate.of(2026, 5, 31));
        plan.setStatus(PlanStatus.DRAFT);
        plan.setDepartmentId(departmentId);
        plan.setCreatedById(UUID.randomUUID());
        return plan;
    }

    private PprTask task(UUID id, PprPlan plan) {
        PprTask task = new PprTask();
        task.setId(id);
        task.setCode("PPR-TASK-2026-0001");
        task.setPlan(plan);
        task.setRegulationId(UUID.randomUUID());
        task.setEquipmentId(UUID.randomUUID());
        task.setTitle("Manual task");
        task.setScheduledStart(LocalDateTime.of(2026, 5, 1, 9, 0));
        task.setScheduledEnd(LocalDateTime.of(2026, 5, 1, 11, 0));
        task.setDueDate(LocalDateTime.of(2026, 5, 2, 9, 0));
        task.setStatus(PprTaskStatus.PLANNED);
        task.setPriority(PriorityLevel.MEDIUM);
        task.setPlannedLaborHours(2.0);
        return task;
    }

    private PprPlanDto planDto(UUID id, UUID departmentId) {
        return new PprPlanDto(
                id,
                "PPR-2026-0001",
                "May PPR",
                PlanStatus.DRAFT,
                departmentId,
                "Maintenance",
                UUID.randomUUID(),
                null,
                null,
                List.of(),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
    }

    private PprTaskDto taskDto(UUID planId) {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 9, 0);

        UUID regulationId = UUID.randomUUID();
        UUID equipmentMaintenanceRuleId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        return new PprTaskDto(
                UUID.randomUUID(),
                "PPR-TASK-2026-0001",
                planId,
                regulationId,
                equipmentMaintenanceRuleId,
                "RULE-001",          // equipmentMaintenanceRuleCode
                "Manual rule",       // equipmentMaintenanceRuleName
                equipmentId,          // equipmentId
                "Manual task",       // title
                start,
                start.plusHours(2),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 1),
                start.plusDays(1),
                PprTaskStatus.PLANNED,
                PriorityLevel.MEDIUM,
                2.0,
                null,
                null
        );
    }

    private String planPayload(UUID departmentId) {
        return """
                {
                  "name": "May PPR",
                  "departmentId": "%s",
                  "createdById": "%s",
                  "fromDate": "2026-05-01",
                  "toDate": "2026-05-31"
                }
                """.formatted(departmentId, UUID.randomUUID());
    }

    private String taskPayload() {
        return """
                {
                  "regulationId": "%s",
                  "equipmentId": "%s",
                  "title": "Manual task",
                  "startDate": "2026-05-01",
                  "endDate": "2026-05-02",
                  "priority": "MEDIUM",
                  "plannedLaborHours": 2.0
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }
}

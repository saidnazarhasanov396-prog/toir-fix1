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
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.service.ApprovalService;
import com.toir.service.PprGeneratorService;
import com.toir.service.PprPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PprPlanController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacPprSecurityTest.SecurityBeans.class
})
class RbacPprSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    PprPlanService pprPlanService;

    @MockBean
    PprGeneratorService pprGeneratorService;

    @MockBean
    PprPlanRepository pprPlanRepository;

    @MockBean
    PprTaskRepository pprTaskRepository;

    @MockBean
    ScopeAccessService scopeAccessService;

    @MockBean
    ApprovalService approvalService;

    @BeforeEach
    void setUpPbacBypass() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        lenient().when(pprPlanRepository.findByIdAndIsDeletedFalse(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(planEntity(invocation.getArgument(0), UUID.randomUUID())));
        lenient().when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(taskEntity(invocation.getArgument(0), planEntity(UUID.randomUUID(), UUID.randomUUID()))));
    }

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return properties;
        }
    }

    @Test
    void unauthenticatedCannotReadPprPlans() throws Exception {
        mockMvc.perform(get("/api/v1/ppr-plans?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadPprPlans() throws Exception {
        mockMvc.perform(get("/api/v1/ppr-plans?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_READ)
    void pprPlanReadCanReadPlanListDetailAndStats() throws Exception {
        UUID planId = UUID.randomUUID();
        when(pprPlanService.findAll(null, null, null, null, 0, 1))
                .thenReturn(new PageImpl<>(List.of(planDto(planId)), PageRequest.of(0, 1), 1));
        when(pprPlanService.findById(planId)).thenReturn(planDto(planId));
        when(pprPlanService.getStats(null, null, null, null))
                .thenReturn(new PprPlanStatsResponse(1, 1, 0, 0, 0, 0, 0));

        mockMvc.perform(get("/api/v1/ppr-plans?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ppr-plans/{id}", planId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ppr-plans/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadPprPlans() throws Exception {
        when(pprPlanService.findAll(null, null, null, null, 0, 1)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/ppr-plans?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadPprPlans() throws Exception {
        when(pprPlanService.findAll(null, null, null, null, 0, 1)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/ppr-plans?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_CREATE)
    void pprPlanCreateCanCreatePlan() throws Exception {
        when(pprPlanService.create(any())).thenReturn(planDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_READ)
    void pprPlanReadCannotCreatePlan() throws Exception {
        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotMutatePprPlan() throws Exception {
        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_UPDATE)
    void pprPlanUpdateCanUpdatePlan() throws Exception {
        UUID planId = UUID.randomUUID();
        when(pprPlanService.update(eq(planId), any())).thenReturn(planDto(planId));

        mockMvc.perform(patch("/api/v1/ppr-plans/{id}", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planPayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_DELETE)
    void pprPlanDeleteCanDeletePlan() throws Exception {
        mockMvc.perform(delete("/api/v1/ppr-plans/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_APPROVE)
    void pprPlanApproveCanApprovePlan() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        when(pprPlanService.findById(planId)).thenReturn(planDto(planId));

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/approve", planId)
                        .param("approverId", approverId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_GENERATE)
    void pprPlanGenerateCanGenerateTasks() throws Exception {
        UUID planId = UUID.randomUUID();
        when(pprGeneratorService.generateForPlan(planId))
                .thenReturn(new PprGeneratorService.GenerationResult(planId, 1, 0));

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/generate", planId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {
            PermissionConstants.PPR_PLAN_GENERATE,
            PermissionConstants.WORK_ORDER_CREATE
    })
    void pprPlanGenerateCanGenerateWorkOrders() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        when(pprGeneratorService.generateWorkOrdersForPlan(planId, createdById))
                .thenReturn(new PprGeneratorService.WorkOrderGenerationResult(
                        planId,
                        1,
                        0,
                        List.of(UUID.randomUUID()),
                        List.of()
                ));

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/work-orders/generate", planId)
                        .param("createdById", createdById.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_READ)
    void pprPlanReadCannotGenerateTasks() throws Exception {
        mockMvc.perform(post("/api/v1/ppr-plans/{id}/generate", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_READ)
    void pprPlanReadCannotGenerateWorkOrders() throws Exception {
        mockMvc.perform(post("/api/v1/ppr-plans/{id}/work-orders/generate", UUID.randomUUID())
                        .param("createdById", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_READ)
    void pprTaskReadCanReadPlanTasks() throws Exception {
        when(pprPlanService.findTasksByPlan(any())).thenReturn(List.of(taskDto(UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/ppr-plans/{id}/tasks?page=0&size=1", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_READ)
    void pprPlanReadCannotReadTaskList() throws Exception {
        mockMvc.perform(get("/api/v1/ppr-plans/{id}/tasks?page=0&size=1", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_CREATE)
    void pprTaskCreateCanCreateTask() throws Exception {
        UUID planId = UUID.randomUUID();
        when(pprPlanService.addTask(eq(planId), any())).thenReturn(taskDto(planId));

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/tasks", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_READ)
    void pprTaskReadCannotCreateTask() throws Exception {
        mockMvc.perform(post("/api/v1/ppr-plans/{id}/tasks", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_POSTPONE)
    void pprTaskPostponeCanPostponeTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(pprPlanService.postponeTask(eq(taskId), any())).thenReturn(taskDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/ppr-plans/tasks/{taskId}/postpone", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newDueDate": "2026-06-03T09:00:00",
                                  "reason": "Parts delay"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_APPROVE)
    void pprTaskApproveCanApproveTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(pprPlanService.approveTask(taskId)).thenReturn(taskDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/ppr-plans/tasks/{taskId}/approve", taskId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_START)
    void pprTaskStartCanStartTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(pprPlanService.startTask(taskId)).thenReturn(taskDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/ppr-plans/tasks/{taskId}/start", taskId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_READ)
    void pprTaskReadCannotStartTask() throws Exception {
        mockMvc.perform(post("/api/v1/ppr-plans/tasks/{taskId}/start", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotStartTask() throws Exception {
        mockMvc.perform(post("/api/v1/ppr-plans/tasks/{taskId}/start", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_COMPLETE)
    void pprTaskCompleteCanCompleteTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(pprPlanService.completeTask(eq(taskId), anyDouble())).thenReturn(taskDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/ppr-plans/tasks/{taskId}/complete", taskId)
                        .param("actualLaborHours", "2.0"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_CANCEL)
    void pprTaskCancelCanCancelTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(pprPlanService.cancelTask(taskId, "No longer needed")).thenReturn(taskDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/ppr-plans/tasks/{taskId}/cancel", taskId)
                        .param("reason", "No longer needed"))
                .andExpect(status().isOk());
    }

    private static String planPayload() {
        return """
                {
                  "name": "June PPR",
                  "createdById": "%s",
                  "fromDate": "2026-06-01",
                  "toDate": "2026-06-30"
                }
                """.formatted(UUID.randomUUID());
    }

    private static String taskPayload() {
        return """
                {
                  "regulationId": "%s",
                  "equipmentId": "%s",
                  "title": "Manual PPR task",
                  "startDate": "2026-06-01",
                  "endDate": "2026-06-02",
                  "priority": "MEDIUM",
                  "plannedLaborHours": 2.0
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private static PprPlanDto planDto(UUID planId) {
        return new PprPlanDto(
                planId,
                "PPR-2026-0001",
                "June PPR",
                PlanStatus.DRAFT,
                UUID.randomUUID(),
                "Maintenance",
                UUID.randomUUID(),
                null,
                null,
                List.of(),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
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

    private static PprPlan planEntity(UUID id, UUID departmentId) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("June PPR");
        plan.setStartDate(LocalDate.of(2026, 6, 1));
        plan.setEndDate(LocalDate.of(2026, 6, 30));
        plan.setStatus(PlanStatus.DRAFT);
        plan.setDepartmentId(departmentId);
        plan.setCreatedById(UUID.randomUUID());
        return plan;
    }

    private static PprTask taskEntity(UUID id, PprPlan plan) {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 0);
        PprTask task = new PprTask();
        task.setId(id);
        task.setCode("PPR-TASK-2026-0001");
        task.setPlan(plan);
        task.setRegulationId(UUID.randomUUID());
        task.setEquipmentId(UUID.randomUUID());
        task.setTitle("Manual PPR task");
        task.setScheduledStart(start);
        task.setScheduledEnd(start.plusHours(2));
        task.setDueDate(start.plusDays(1));
        task.setStatus(PprTaskStatus.PLANNED);
        task.setPriority(PriorityLevel.MEDIUM);
        task.setPlannedLaborHours(2.0);
        return task;
    }
}

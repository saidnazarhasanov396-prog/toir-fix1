package com.toir.controller;

import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.dto.pprplanning.PprPlanStatsResponse;
import com.toir.entity.PprPlan;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.ApprovalService;
import com.toir.service.PprGeneratorService;
import com.toir.service.PprPlanService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PprPlanControllerContractTest {

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

    @Mock
    ApprovalService approvalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        mockMvc = MockMvcBuilders.standaloneSetup(new PprPlanController(service, approvalService, generatorService, planRepository, taskRepository, scopeAccessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listPlansPassesDateAndDepartmentFiltersToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        PprPlanDto plan = new PprPlanDto(
                planId,
                "PPR-2026-0001",
                "May plan",
                PlanStatus.DRAFT,
                departmentId,
                "Mechanical",
                UUID.randomUUID(),
                null,
                null,
                List.of(),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(scopeAccessService.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(service.findAll(2026, 5, 12, departmentId, 0, 20))
                .thenReturn(new PageImpl<>(List.of(plan), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/ppr-plans")
                        .param("year", "2026")
                        .param("month", "5")
                        .param("day", "12")
                        .param("departmentId", departmentId.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(planId.toString()))
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()));

        verify(service).findAll(2026, 5, 12, departmentId, 0, 20);
    }

    @Test
    void listPlansWithoutFiltersKeepsOldListBehavior() throws Exception {
        when(service.findAll(null, null, null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/ppr-plans")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(service).findAll(null, null, null, null, 0, 20);
    }

    @Test
    void listAndGetByIdReturnSamePlanId() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        PprPlanDto plan = new PprPlanDto(
                planId,
                "PPR-2026-0099",
                "June plan",
                PlanStatus.DRAFT,
                departmentId,
                "Instrumentation",
                UUID.randomUUID(),
                null,
                null,
                List.of(),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        );

        when(service.findAll(null, null, null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(plan), PageRequest.of(0, 20), 1));
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, departmentId)));
        when(service.findById(planId)).thenReturn(plan);

        mockMvc.perform(get("/api/v1/ppr-plans")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(planId.toString()));

        mockMvc.perform(get("/api/v1/ppr-plans/{id}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId.toString()));
    }

    @Test
    void getByIdReturnsNotFoundWithCleanErrorMessage() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/ppr-plans/{id}", missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PPR plan not found: " + missingId));
    }

    @Test
    void statsPassesDepartmentFilterToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        PprPlanStatsResponse stats = new PprPlanStatsResponse(
                6,
                1,
                2,
                3,
                8,
                4,
                5
        );
        when(scopeAccessService.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(service.getStats(2026, 5, 12, departmentId)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/ppr-plans/stats")
                        .param("year", "2026")
                        .param("month", "5")
                        .param("day", "12")
                        .param("departmentId", departmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlans").value(6))
                .andExpect(jsonPath("$.draftPlans").value(1))
                .andExpect(jsonPath("$.generatedPlans").value(2))
                .andExpect(jsonPath("$.approvedPlans").value(3))
                .andExpect(jsonPath("$.plannedTasks").value(8))
                .andExpect(jsonPath("$.inProgressTasks").value(4))
                .andExpect(jsonPath("$.completedTasks").value(5));

        verify(service).getStats(2026, 5, 12, departmentId);
    }

    @Test
    void statsRouteIsNotSwallowedByIdRoute() throws Exception {
        when(service.getStats(null, null, null, null)).thenReturn(new PprPlanStatsResponse(0, 0, 0, 0, 0, 0, 0));

        mockMvc.perform(get("/api/v1/ppr-plans/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlans").value(0));

        verify(service).getStats(null, null, null, null);
        verify(service, never()).findById(any(UUID.class));
    }

    @Test
    void createPlanAcceptsDateRangeWithoutYearMonth() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        PprPlanDto plan = new PprPlanDto(
                planId,
                "PPR-2026-0001",
                "Q2 plan",
                PlanStatus.DRAFT,
                departmentId,
                "Mechanical",
                createdById,
                null,
                null,
                List.of(),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 6, 30)
        );
        when(service.create(any())).thenReturn(plan);

        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Q2 plan",
                                  "fromDate": "2026-04-01",
                                  "toDate": "2026-06-30",
                                  "departmentId": "%s",
                                  "createdById": "%s"
                                }
                                """.formatted(departmentId, createdById)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(planId.toString()))
                .andExpect(jsonPath("$.year").doesNotExist())
                .andExpect(jsonPath("$.month").doesNotExist())
                .andExpect(jsonPath("$.fromDate").value("2026-04-01"))
                .andExpect(jsonPath("$.toDate").value("2026-06-30"));

        verify(service).create(any());
    }

    @Test
    void createTaskWithoutCodeReturnsGeneratedCode() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 0);
        String generatedCode = "PPR-TASK-" + Year.now().getValue() + "-0001";
        PprTaskDto dto = new PprTaskDto(
                taskId,
                generatedCode,
                planId,
                regulationId,
                equipmentId,
                "Manual PPR task",
                start,
                start.plusHours(2),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 1),
                start.plusDays(1),
                PprTaskStatus.PLANNED,
                PriorityLevel.MEDIUM,
                2.0,
                null,
                null
        );
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, UUID.randomUUID())));
        when(service.addTask(eq(planId), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/tasks", planId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "regulationId": "%s",
                                  "equipmentId": "%s",
                                  "title": "Manual PPR task",
                                  "scheduledStart": "2026-06-01T09:00:00",
                                  "scheduledEnd": "2026-06-01T11:00:00",
                                  "dueDate": "2026-06-02T09:00:00",
                                  "priority": "MEDIUM",
                                  "plannedLaborHours": 2.0
                                }
                                """.formatted(regulationId, equipmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.code").value(generatedCode))
                .andExpect(jsonPath("$.startDate").value("2026-06-01"))
                .andExpect(jsonPath("$.endDate").value("2026-06-01"));
    }

    @Test
    void createTaskAllowsMissingEquipment() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 0);
        String generatedCode = "PPR-TASK-" + Year.now().getValue() + "-0001";
        PprTaskDto dto = new PprTaskDto(
                taskId,
                generatedCode,
                planId,
                regulationId,
                null,
                "Manual PPR task",
                start,
                start.plusHours(2),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 1),
                start.plusDays(1),
                PprTaskStatus.PLANNED,
                PriorityLevel.MEDIUM,
                2.0,
                null,
                null
        );
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, UUID.randomUUID())));
        when(service.addTask(eq(planId), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/tasks", planId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "regulationId": "%s",
                                  "title": "Manual PPR task",
                                  "scheduledStart": "2026-06-01T09:00:00",
                                  "scheduledEnd": "2026-06-01T11:00:00",
                                  "dueDate": "2026-06-02T09:00:00",
                                  "priority": "MEDIUM",
                                  "plannedLaborHours": 2.0
                                }
                                """.formatted(regulationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.equipmentId").doesNotExist());
    }

    @Test
    void createTaskWithCodeReturnsBadRequest() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, UUID.randomUUID())));
        when(service.addTask(eq(planId), any())).thenThrow(RestException.badRequest(
                "code is generated by backend and must not be provided"));

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/tasks", planId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "PPR-TASK-2026-0099",
                                  "regulationId": "%s",
                                  "equipmentId": "%s",
                                  "title": "Manual PPR task",
                                  "scheduledStart": "2026-06-01T09:00:00",
                                  "scheduledEnd": "2026-06-01T11:00:00",
                                  "dueDate": "2026-06-02T09:00:00",
                                  "priority": "MEDIUM",
                                  "plannedLaborHours": 2.0
                                }
                                """.formatted(regulationId, equipmentId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("code is generated by backend and must not be provided"));
    }

    private PprPlan plan(UUID id, UUID departmentId) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("May plan");
        plan.setStartDate(LocalDate.of(2026, 5, 1));
        plan.setEndDate(LocalDate.of(2026, 5, 31));
        plan.setStatus(PlanStatus.DRAFT);
        plan.setDepartmentId(departmentId);
        plan.setCreatedById(UUID.randomUUID());
        return plan;
    }
}

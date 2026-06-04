package com.toir.controller;

import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.dto.pprplanning.PprPlanRequest;
import com.toir.dto.pprplanning.PprPlanStatsResponse;
import com.toir.entity.PprPlan;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprFrequency;
import com.toir.enums.PprScheduleType;
import com.toir.enums.PprScopeType;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PprType;
import com.toir.enums.PriorityLevel;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.exception.RestException;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.security.ScopeAccessService;

import com.toir.service.PprGeneratorService;
import com.toir.service.PprPlanService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.assertThat;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        mockMvc = MockMvcBuilders.standaloneSetup(new PprPlanController(service, generatorService, planRepository, taskRepository, scopeAccessService))
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
                .andExpect(jsonPath("$.content[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.content[0].taskCount").value(0))
                .andExpect(jsonPath("$.content[0].tasks").isArray())
                .andExpect(jsonPath("$.content[0].tasks.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.last").value(true));

        verify(service).findAll(2026, 5, 12, departmentId, 0, 20);
    }

    @Test
    void listPlansWithoutFiltersKeepsOldListBehavior() throws Exception {
        when(service.findAllUnpaged(null, null, null, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/ppr-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.last").value(true));

        verify(service).findAllUnpaged(null, null, null, null);
    }

    @Test
    void listAndGetByIdReturnSamePlanId() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        PprTaskDto task = taskDto(planId, equipmentId, "Pump 17", regulationId, "Monthly inspection");
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
                List.of(task),
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
                .andExpect(jsonPath("$.content[0].id").value(planId.toString()))
                .andExpect(jsonPath("$.content[0].taskCount").value(1))
                .andExpect(jsonPath("$.content[0].tasks").isArray())
                .andExpect(jsonPath("$.content[0].tasks[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.content[0].tasks[0].equipmentName").value("Pump 17"))
                .andExpect(jsonPath("$.content[0].tasks[0].regulationId").value(regulationId.toString()))
                .andExpect(jsonPath("$.content[0].tasks[0].regulationName").value("Monthly inspection"));

        mockMvc.perform(get("/api/v1/ppr-plans/{id}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(planId.toString()))
                .andExpect(jsonPath("$.taskCount").value(1))
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.tasks[0].equipmentId").value(equipmentId.toString()))
                .andExpect(jsonPath("$.tasks[0].equipmentName").value("Pump 17"))
                .andExpect(jsonPath("$.tasks[0].regulationId").value(regulationId.toString()))
                .andExpect(jsonPath("$.tasks[0].regulationName").value("Monthly inspection"));
    }

    @Test
    void listWithoutPaginationReturnsAllPlansAndTaskCount() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID firstEquipmentId = UUID.randomUUID();
        UUID firstRegulationId = UUID.randomUUID();
        List<PprTaskDto> tasks = List.of(
                taskDto(planId, firstEquipmentId, "Compressor A", firstRegulationId, "Quarterly PM"),
                taskDto(planId, UUID.randomUUID(), "Pump B", UUID.randomUUID(), "Monthly PM"),
                taskDto(planId, UUID.randomUUID(), "Fan C", UUID.randomUUID(), "Weekly PM"),
                taskDto(planId, UUID.randomUUID(), "Valve D", UUID.randomUUID(), "Annual PM")
        );
        PprPlanDto plan = new PprPlanDto(
                planId,
                "PPR-2026-0100",
                "All plans item",
                PlanStatus.DRAFT,
                departmentId,
                "Instrumentation",
                UUID.randomUUID(),
                null,
                null,
                tasks,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        );
        when(service.findAllUnpaged(null, null, null, null))
                .thenReturn(new PageImpl<>(List.of(plan), PageRequest.of(0, 1), 1));

        mockMvc.perform(get("/api/v1/ppr-plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(planId.toString()))
                .andExpect(jsonPath("$.content[0].taskCount").value(4))
                .andExpect(jsonPath("$.content[0].tasks").isArray())
                .andExpect(jsonPath("$.content[0].tasks.length()").value(4))
                .andExpect(jsonPath("$.content[0].tasks[0].equipmentId").value(firstEquipmentId.toString()))
                .andExpect(jsonPath("$.content[0].tasks[0].equipmentName").value("Compressor A"))
                .andExpect(jsonPath("$.content[0].tasks[0].regulationId").value(firstRegulationId.toString()))
                .andExpect(jsonPath("$.content[0].tasks[0].regulationName").value("Quarterly PM"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));

        verify(service).findAllUnpaged(null, null, null, null);
    }

    @Test
    void listWithoutPaginationStillPassesFiltersToService() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(departmentId)).thenReturn(departmentId);
        when(service.findAllUnpaged(2026, 5, 12, departmentId))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/ppr-plans")
                        .param("year", "2026")
                        .param("month", "5")
                        .param("day", "12")
                        .param("departmentId", departmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.last").value(true));

        verify(service).findAllUnpaged(2026, 5, 12, departmentId);
    }

    @Test
    void listWithOnlyOnePaginationParameterReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/ppr-plans")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Both page and size must be provided for paginated PPR plan list"));
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
        PprTaskDto generatedTask = taskDto(planId, UUID.randomUUID(), "Pump 17", UUID.randomUUID(), "Monthly PM");
        PprPlanDto plan = new PprPlanDto(
                planId,
                "PPR-2026-0001",
                "Q2 plan",
                PlanStatus.GENERATED,
                departmentId,
                "Mechanical",
                createdById,
                null,
                null,
                List.of(generatedTask),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 6, 30)
        ).withGenerationMessage("PPR plan was created and 1 PPR task(s) were generated.");
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
                .andExpect(jsonPath("$.toDate").value("2026-06-30"))
                .andExpect(jsonPath("$.status").value("GENERATED"))
                .andExpect(jsonPath("$.taskCount").value(1))
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.generationMessage").value("PPR plan was created and 1 PPR task(s) were generated."));

        verify(service).create(any());
    }

    @Test
    void createPlanDefaultsMissingDepartmentForNormalScopedUser() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(departmentId);
        PprPlanDto plan = new PprPlanDto(
                planId,
                "PPR-2026-0001",
                "Scoped plan",
                PlanStatus.DRAFT,
                departmentId,
                "Mechanical",
                createdById,
                null,
                null,
                List.of(),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        );
        when(service.create(any())).thenReturn(plan);

        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Scoped plan",
                                  "fromDate": "2026-06-01",
                                  "toDate": "2026-06-30",
                                  "createdById": "%s"
                                }
                                """.formatted(createdById)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()));

        ArgumentCaptor<PprPlanRequest> requestCaptor = ArgumentCaptor.forClass(PprPlanRequest.class);
        verify(service).create(requestCaptor.capture());
        assertThat(requestCaptor.getValue().departmentId()).isEqualTo(departmentId);
        verify(scopeAccessService).assertCanAccessDepartment(departmentId);
    }

    @Test
    void createPlanAcceptsPhase1ContractFields() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        PprTaskDto generatedTask = taskDto(planId, equipmentId, "Pump 17", regulationId, "Monthly preventive");
        PprPlanDto plan = new PprPlanDto(
                planId,
                "PPR-2026-0002",
                "Monthly preventive",
                PlanStatus.GENERATED,
                departmentId,
                "Mechanical",
                createdById,
                null,
                "phase1",
                List.of(generatedTask),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                PprType.PREVENTIVE_MAINTENANCE,
                PprScheduleType.CALENDAR,
                PprFrequency.MONTHLY,
                null,
                PprScopeType.DEPARTMENT,
                List.of()
        ).withGenerationMessage("PPR plan was created and 1 PPR task(s) were generated.");
        when(service.create(any())).thenReturn(plan);

        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Monthly preventive",
                                  "fromDate": "2026-06-01",
                                  "toDate": "2026-06-30",
                                  "departmentId": "%s",
                                  "createdById": "%s",
                                  "notes": "phase1",
                                  "pprType": "PREVENTIVE_MAINTENANCE",
                                  "scheduleType": "CALENDAR",
                                  "frequency": "MONTHLY",
                                  "scopeType": "DEPARTMENT",
                                  "equipmentIds": ["%s"],
                                  "equipmentTypeIds": ["%s"],
                                  "regulationIds": ["%s"]
                                }
                                """.formatted(departmentId, createdById, equipmentId, equipmentTypeId, regulationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pprType").value("PREVENTIVE_MAINTENANCE"))
                .andExpect(jsonPath("$.scheduleType").value("CALENDAR"))
                .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.scopeType").value("DEPARTMENT"))
                .andExpect(jsonPath("$.status").value("GENERATED"))
                .andExpect(jsonPath("$.taskCount").value(1))
                .andExpect(jsonPath("$.targets").isArray());

        ArgumentCaptor<PprPlanRequest> captor = ArgumentCaptor.forClass(PprPlanRequest.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().pprType()).isEqualTo(PprType.PREVENTIVE_MAINTENANCE);
        assertThat(captor.getValue().scheduleType()).isEqualTo(PprScheduleType.CALENDAR);
        assertThat(captor.getValue().frequency()).isEqualTo(PprFrequency.MONTHLY);
        assertThat(captor.getValue().scopeType()).isEqualTo(PprScopeType.DEPARTMENT);
        assertThat(captor.getValue().equipmentIds()).containsExactly(equipmentId);
        assertThat(captor.getValue().equipmentTypeIds()).containsExactly(equipmentTypeId);
        assertThat(captor.getValue().regulationIds()).containsExactly(regulationId);
    }

    @Test
    void generateWorkOrdersFromPprPlanReturnsCreatedAndSkippedResult() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID createdById = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID skippedTaskId = UUID.randomUUID();
        when(planRepository.findByIdAndIsDeletedFalse(planId)).thenReturn(Optional.of(plan(planId, UUID.randomUUID())));
        when(generatorService.generateWorkOrdersForPlan(planId, createdById))
                .thenReturn(new PprGeneratorService.WorkOrderGenerationResult(
                        planId,
                        1,
                        1,
                        List.of(workOrderId),
                        List.of(new PprGeneratorService.WorkOrderGenerationSkippedItem(
                                skippedTaskId,
                                "WORK_ORDER_ALREADY_EXISTS"
                        ))
                ));

        mockMvc.perform(post("/api/v1/ppr-plans/{id}/work-orders/generate", planId)
                        .param("createdById", createdById.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(planId.toString()))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.createdWorkOrderIds[0]").value(workOrderId.toString()))
                .andExpect(jsonPath("$.skippedItems[0].pprTaskId").value(skippedTaskId.toString()))
                .andExpect(jsonPath("$.skippedItems[0].reason").value("WORK_ORDER_ALREADY_EXISTS"));

        verify(generatorService).generateWorkOrdersForPlan(planId, createdById);
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
                null, // equipmentMaintenanceRuleId
                null, // equipmentMaintenanceRuleCode
                null, // equipmentMaintenanceRuleName
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
                null, // equipmentMaintenanceRuleId
                null, // equipmentMaintenanceRuleCode
                null, // equipmentMaintenanceRuleName
                null, // equipmentId
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

    private PprTaskDto taskDto(
            UUID planId,
            UUID equipmentId,
            String equipmentName,
            UUID regulationId,
            String regulationName
    ) {
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 0);
        return new PprTaskDto(
                UUID.randomUUID(),
                "PPR-TASK-2026-0001",
                planId,
                regulationId,
                regulationName,
                null,
                null,
                null,
                equipmentId,
                equipmentName,
                "Calendar task",
                start,
                start.plusHours(2),
                start.toLocalDate(),
                start.toLocalDate(),
                start.plusDays(1),
                PprTaskStatus.PLANNED,
                PriorityLevel.MEDIUM,
                2.0,
                null,
                null
        );
    }
}

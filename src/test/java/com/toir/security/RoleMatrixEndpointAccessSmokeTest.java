package com.toir.security;

import com.toir.controller.ActualCostController;
import com.toir.controller.PprPlanController;
import com.toir.controller.ProcurementRequestController;
import com.toir.controller.SparePartController;
import com.toir.controller.WarehouseController;
import com.toir.controller.maintenance.MaintenanceBudgetController;
import com.toir.dto.actualcost.ActualCostDto;
import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.sparepart.SparePartDto;
import com.toir.dto.sparepart.SparePartRequest;
import com.toir.dto.warehouse.WarehouseDto;
import com.toir.dto.warehouse.WarehouseRequest;
import com.toir.entity.PprPlan;
import com.toir.entity.PprTask;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.BudgetStatus;
import com.toir.enums.PlanStatus;
import com.toir.enums.PprTaskStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.repository.PprPlanRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.service.ActualCostService;
import com.toir.service.ApprovalService;
import com.toir.service.PprGeneratorService;
import com.toir.service.PprPlanService;
import com.toir.service.ProcurementRequestService;
import com.toir.service.SparePartService;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.service.WarehouseService;
import com.toir.service.maintanance.MaintenanceBudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        PprPlanController.class,
        WarehouseController.class,
        SparePartController.class,
        ProcurementRequestController.class,
        MaintenanceBudgetController.class,
        ActualCostController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RoleMatrixEndpointAccessSmokeTest.SecurityBeans.class
})
class RoleMatrixEndpointAccessSmokeTest {

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
    WarehouseService warehouseService;

    @MockBean
    WarehouseEquipmentItemService warehouseEquipmentItemService;

    @MockBean
    SparePartService sparePartService;

    @MockBean
    ProcurementRequestService procurementRequestService;

    @MockBean
    MaintenanceBudgetService budgetService;

    @MockBean
    ActualCostService actualCostService;

    @MockBean
    ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        lenient().when(pprPlanRepository.findByIdAndIsDeletedFalse(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(planEntity(invocation.getArgument(0))));
        lenient().when(pprTaskRepository.findByIdAndIsDeletedFalseWithPlan(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(taskEntity(invocation.getArgument(0), planEntity(UUID.randomUUID()))));
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
    @WithMockUser(authorities = {
            "PPR_ENGINEER",
            PermissionConstants.PPR_PLAN_READ,
            PermissionConstants.PPR_PLAN_CREATE,
            PermissionConstants.PPR_PLAN_UPDATE,
            PermissionConstants.PPR_PLAN_GENERATE,
            PermissionConstants.PPR_TASK_READ,
            PermissionConstants.PPR_TASK_CREATE,
            PermissionConstants.PPR_TASK_START,
            PermissionConstants.PPR_TASK_COMPLETE,
            PermissionConstants.PPR_TASK_POSTPONE,
            PermissionConstants.EQUIPMENT_READ,
            PermissionConstants.WORK_ORDER_READ,
            PermissionConstants.REPAIR_REQUEST_READ,
            PermissionConstants.KNOWLEDGE_READ,
            PermissionConstants.READ_LEGACY
    })
    void pprEngineerCanReadPprButCannotApprovePlan() throws Exception {
        UUID planId = UUID.randomUUID();
        when(pprPlanService.findAll(null, null, null, null, 0, 1)).thenReturn(Page.empty());
        when(pprPlanService.findTasksByPlan(planId)).thenReturn(List.of(taskDto(planId)));

        mockMvc.perform(get("/api/v1/ppr-plans?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ppr-plans/{id}/tasks?page=0&size=1", planId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ppr-plans/{id}/approve", planId)
                        .param("approverId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {
            "STOREKEEPER",
            PermissionConstants.WAREHOUSE_READ,
            PermissionConstants.WAREHOUSE_EQUIPMENT_READ,
            PermissionConstants.WAREHOUSE_EQUIPMENT_STATUS_UPDATE,
            PermissionConstants.STOCK_READ,
            PermissionConstants.STOCK_RECEIVE,
            PermissionConstants.STOCK_ISSUE,
            PermissionConstants.STOCK_MOVE,
            PermissionConstants.MATERIAL_USAGE_READ,
            PermissionConstants.MATERIAL_USAGE_ISSUE,
            PermissionConstants.SPARE_PART_READ,
            PermissionConstants.SPARE_PART_CREATE,
            PermissionConstants.SPARE_PART_UPDATE,
            PermissionConstants.WORK_ORDER_READ,
            PermissionConstants.EQUIPMENT_READ,
            PermissionConstants.PROCUREMENT_READ,
            PermissionConstants.READ_LEGACY
    })
    void storekeeperCanReadWarehouseAndSparePartsButCannotApproveProcurement() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        when(warehouseService.findAll(null, null, null, null, null)).thenReturn(List.of(warehouseDto(warehouseId)));
        when(warehouseService.findStocks(warehouseId)).thenReturn(List.of());
        when(sparePartService.findAll(1, 0, null, "", null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/warehouses?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/warehouses/{id}/stocks?page=0&size=1", warehouseId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/spare-parts?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/approve", sparePartId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {
            "SUPPLY_SPECIALIST",
            PermissionConstants.PROCUREMENT_READ,
            PermissionConstants.PROCUREMENT_CREATE,
            PermissionConstants.PROCUREMENT_SUBMIT,
            PermissionConstants.PROCUREMENT_ORDER,
            PermissionConstants.PROCUREMENT_RECEIVE,
            PermissionConstants.PROCUREMENT_CANCEL,
            PermissionConstants.WAREHOUSE_READ,
            PermissionConstants.STOCK_READ,
            PermissionConstants.SPARE_PART_READ,
            PermissionConstants.SPARE_PART_CREATE,
            PermissionConstants.SPARE_PART_UPDATE,
            PermissionConstants.ANALYTICS_READ,
            PermissionConstants.READ_LEGACY
    })
    void supplySpecialistCanCreateAndProgressProcurementButCannotApproveOrReject() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(procurementRequestService.findAll(null, null,null)).thenReturn(List.of(procurementRequestDto(requestId)));
        when(procurementRequestService.create(any())).thenReturn(procurementRequestDto(requestId));
        when(procurementRequestService.submit(requestId)).thenReturn(procurementRequestDto(requestId));
        when(procurementRequestService.markOrdered(requestId)).thenReturn(procurementRequestDto(requestId));
        when(procurementRequestService.markReceived(requestId)).thenReturn(procurementRequestDto(requestId));
        when(procurementRequestService.cancel(requestId)).thenReturn(procurementRequestDto(requestId));

        mockMvc.perform(get("/api/v1/procurement-requests?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/procurement-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(procurementRequestPayload()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/submit", requestId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/ordered", requestId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/received", requestId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/cancel", requestId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/approve", requestId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/reject", requestId)
                        .param("reason", "duplicate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {
            "ECONOMIST",
            PermissionConstants.ACTUAL_COST_READ,
            PermissionConstants.ACTUAL_COST_CREATE,
            PermissionConstants.BUDGET_READ,
            PermissionConstants.BUDGET_CREATE,
            PermissionConstants.BUDGET_UPDATE,
            PermissionConstants.ANALYTICS_READ,
            PermissionConstants.ANALYTICS_EXPORT,
            PermissionConstants.APPROVAL_READ,
            PermissionConstants.APPROVAL_CREATE,
            PermissionConstants.READ_LEGACY
    })
    void economistCanReadBudgetsAndActualCostsButCannotApproveActualCosts() throws Exception {
        UUID costId = UUID.randomUUID();
        when(budgetService.findByYear(2026)).thenReturn(List.of());
        when(actualCostService.findByFilters(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/budgets?year=2026&page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/actual-costs?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/actual-costs/{id}/approve", costId)
                        .param("reviewerId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {
            "VIEWER",
            PermissionConstants.EQUIPMENT_READ,
            PermissionConstants.REPAIR_REQUEST_READ,
            PermissionConstants.WORK_ORDER_READ,
            PermissionConstants.DEFECT_READ,
            PermissionConstants.DEFECT_LIST_READ,
            PermissionConstants.PPR_PLAN_READ,
            PermissionConstants.PPR_TASK_READ,
            PermissionConstants.KNOWLEDGE_READ,
            PermissionConstants.ANALYTICS_READ,
            PermissionConstants.READ_LEGACY
    })
    void viewerCanReadNonSensitiveSampleRouteButCannotMutate() throws Exception {
        when(pprPlanService.findAll(null, null, null, null, 0, 1)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/ppr-plans?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ppr-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM_ADMIN", PermissionConstants.WILDCARD})
    void systemAdminCanReadAndMutateThroughBypass() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID actualCostId = UUID.randomUUID();
        when(warehouseService.findAll(null, null, null, null, null)).thenReturn(List.of());
        when(warehouseService.create(any(WarehouseRequest.class))).thenReturn(warehouseDto(warehouseId));
        when(procurementRequestService.validateCanApprove(requestId)).thenReturn(procurementRequestDto(requestId));
        when(procurementRequestService.findById(requestId)).thenReturn(procurementRequestDto(requestId));
        when(actualCostService.review(eq(actualCostId), eq(true), any(UUID.class), eq("Approved")))
                .thenReturn(actualCostDto(ActualCostStatus.APPROVED));

        mockMvc.perform(get("/api/v1/warehouses?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehousePayload()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/procurement-requests/{id}/approve", requestId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/actual-costs/{id}/approve", actualCostId)
                        .param("reviewerId", UUID.randomUUID().toString())
                        .param("comment", "Approved"))
                .andExpect(status().isOk());
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

    private static PprPlan planEntity(UUID id) {
        PprPlan plan = new PprPlan();
        plan.setId(id);
        plan.setCode("PPR-2026-0001");
        plan.setName("June PPR");
        plan.setStartDate(LocalDate.of(2026, 6, 1));
        plan.setEndDate(LocalDate.of(2026, 6, 30));
        plan.setStatus(PlanStatus.DRAFT);
        plan.setDepartmentId(UUID.randomUUID());
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

    private static WarehouseDto warehouseDto(UUID id) {
        return new WarehouseDto(
                id,
                "WH-001",
                "Main warehouse",
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                new WarehouseDto.Summary(0, 0, 0, 0),
                List.of()
        );
    }

    private static SparePartDto sparePartDto(UUID id) {
        return new SparePartDto(
                id,
                "SPARE_PART",
                "SP-001",
                "Oil filter",
                "SPARE_PART",
                new SparePartDto.UnitRef("pcs", "pcs"),
                "Bosch",
                "OIL-FILTER",
                "Filter",
                1,
                0,
                0,
                0,
                0
        );
    }

    private static ProcurementRequestDto procurementRequestDto(UUID id) {
        return new ProcurementRequestDto(
                id,
                "PR-2026-00001",
                "Replace bearing",
                "Procurement request",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                ProcurementRequestStatus.DRAFT,
                "MANUAL",
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private static ActualCostDto actualCostDto(ActualCostStatus status) {
        return new ActualCostDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                null,
                null,
                null,
                100.0,
                Instant.now(),
                "Test actual cost"
        );
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

    private static String warehousePayload() {
        return """
                {
                  "name": "Main warehouse",
                  "active": true
                }
                """;
    }

    private static String procurementRequestPayload() {
        return """
                {
                  "title": "Replace bearing",
                  "description": "Procurement request",
                  "requiredBy": "2026-06-01",
                  "lines": []
                }
                """;
    }
}

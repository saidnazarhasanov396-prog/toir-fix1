package com.toir.security;

import com.toir.controller.WorkOrderController;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.repository.WorkOrderRepository;
import com.toir.service.ApprovalService;
import com.toir.service.WorkOrderService;
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

import java.time.Instant;
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

@WebMvcTest(controllers = WorkOrderController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWorkOrderSecurityTest.SecurityBeans.class
})
class RbacWorkOrderSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WorkOrderService workOrderService;

    @MockBean
    WorkOrderRepository workOrderRepository;

    @MockBean
    ScopeAccessService scopeAccessService;

    @MockBean
    ApprovalService approvalService;

    @BeforeEach
    void setUpPbacBypass() {
        lenient().when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        lenient().when(scopeAccessService.enforceDepartmentScope(any(UUID.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(workOrderRepository.findByIdAndIsDeletedFalse(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(workOrderEntity(invocation.getArgument(0))));
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
    void unauthenticatedCannotReadWorkOrders() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadWorkOrders() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void workOrderReadCanReadListMobileFeedAndDetail() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderService.search(null, null, null, 0, 1, null))
                .thenReturn(new PageImpl<>(List.of(workOrderDto(workOrderId)), PageRequest.of(0, 1), 1));
        when(workOrderService.mobileFeed(null, null, null, 0, 1))
                .thenReturn(new PageImpl<>(List.of(workOrderDto(workOrderId)), PageRequest.of(0, 1), 1));
        when(workOrderService.findById(workOrderId)).thenReturn(workOrderDto(workOrderId));

        mockMvc.perform(get("/api/v1/work-orders?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/work-orders/mobile-feed?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadWorkOrders() throws Exception {
        when(workOrderService.search(null, null, null, 0, 1, null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/work-orders?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadWorkOrders() throws Exception {
        when(workOrderService.search(null, null, null, 0, 1, null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/work-orders?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_CREATE)
    void workOrderCreateCanCreateWorkOrder() throws Exception {
        when(workOrderService.create(any())).thenReturn(workOrderDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void workOrderReadCannotCreateWorkOrder() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCreateWorkOrder() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotCreateWorkOrder() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_APPROVE)
    void workOrderApproveEndpointIsRemoved() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/work-orders/{id}/approve", workOrderId)
                        .param("approverId", approverId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void workOrderReadCannotApproveWorkOrder() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{id}/approve", UUID.randomUUID())
                        .param("approverId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_START)
    void workOrderStartCanStartWorkOrder() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderService.start(workOrderId)).thenReturn(workOrderDto(workOrderId));

        mockMvc.perform(post("/api/v1/work-orders/{id}/start", workOrderId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void workOrderReadCannotStartWorkOrder() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{id}/start", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_COMPLETE)
    void workOrderCompleteCanCompleteWorkOrder() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderService.complete(eq(workOrderId), any())).thenReturn(workOrderDto(workOrderId));

        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completePayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void workOrderReadCannotCompleteWorkOrder() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_CLOSE)
    void workOrderCloseCanCloseWorkOrder() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderService.close(eq(workOrderId), any())).thenReturn(workOrderDto(workOrderId));

        mockMvc.perform(post("/api/v1/work-orders/{id}/close", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closePayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void workOrderReadCannotCloseWorkOrder() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{id}/close", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotRunLifecycleActions() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{id}/start", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotRunLifecycleActions() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{id}/start", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    private WorkOrderDto workOrderDto(UUID id) {
        return new WorkOrderDto(
                id,
                "WO-2026-1001",
                "Planned repair",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Pump #1",
                "Maintenance",
                null,
                null,
                null,
                null,
                WorkOrderStatus.PLANNED,
                WorkOrderType.PLANNED,
                WorkType.REPAIR,
                PriorityLevel.MEDIUM,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                null,
                "summary",
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                0,
                0
        );
    }

    private WorkOrder workOrderEntity(UUID id) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setNumber("WO-2026-1001");
        workOrder.setTitle("Planned repair");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setStatus(WorkOrderStatus.PLANNED);
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedById(UUID.randomUUID());
        return workOrder;
    }

    private String createPayload() {
        return """
                {
                  "number": "WO-2026-1001",
                  "title": "Planned repair",
                  "equipmentId": "%s",
                  "departmentId": "%s",
                  "type": "PLANNED",
                  "workType": "REPAIR",
                  "priority": "MEDIUM",
                  "createdById": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private String completePayload() {
        return """
                {
                  "result": "done",
                  "summary": "summary"
                }
                """;
    }

    private String closePayload() {
        return """
                {
                  "result": "closed",
                  "closureNotes": "notes"
                }
                """;
    }
}

package com.toir.security;

import com.toir.controller.WorkOrderController;
import com.toir.dto.workorder.CloseWorkOrderRequest;
import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.repository.WorkOrderRepository;
import com.toir.service.WorkOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkOrderPbacScopeTest {

    @Mock
    WorkOrderService service;

    @Mock
    WorkOrderRepository repository;

    @Mock
    ScopeAccessService scopeAccessService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new WorkOrderController(service, repository, scopeAccessService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listClampsRequestedDepartmentToCurrentDepartment() throws Exception {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(currentDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(service.search(null, currentDepartmentId, null, 0, 20, null))
                .thenReturn(new PageImpl<>(List.of(dto(UUID.randomUUID(), currentDepartmentId)),
                        PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/work-orders")
                        .param("departmentId", requestedDepartmentId.toString()))
                .andExpect(status().isOk());

        verify(service).search(null, currentDepartmentId, null, 0, 20, null);
    }

    @Test
    void mobileFeedWithoutDepartmentForNonAdminWithoutDepartmentIsDenied() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);

        mockMvc.perform(get("/api/v1/work-orders/mobile-feed"))
                .andExpect(status().isForbidden());

        verify(service, never()).mobileFeed(any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void systemAdminCanRequestGlobalList() throws Exception {
        when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(service.search(null, null, null, 0, 20, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/work-orders"))
                .andExpect(status().isOk());

        verify(service).search(null, null, null, 0, 20, null);
    }

    @Test
    void detailAllowsSameDepartment() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(service.findById(workOrderId)).thenReturn(dto(workOrderId, departmentId));

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isOk());
    }

    @Test
    void detailDeniesDifferentDepartment() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        doDenyDepartment(departmentId);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isForbidden());

        verify(service, never()).findById(workOrderId);
    }

    @Test
    void missingWorkOrderRemainsNotFound() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAllowsSameDepartment() throws Exception {
        UUID departmentId = UUID.randomUUID();
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(service.create(any())).thenReturn(dto(UUID.randomUUID(), departmentId));

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(createPayload(departmentId)))
                .andExpect(status().isCreated());
    }

    @Test
    void createDeniesDifferentDepartment() throws Exception {
        UUID departmentId = UUID.randomUUID();
        doDenyDepartment(departmentId);

        mockMvc.perform(post("/api/v1/work-orders")
                        .contentType("application/json")
                        .content(createPayload(departmentId)))
                .andExpect(status().isForbidden());

        verify(service, never()).create(any());
    }

    @Test
    void approveChecksDepartmentScopeBeforeMutation() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(service.approve(workOrderId, approverId)).thenReturn(dto(workOrderId, departmentId));

        mockMvc.perform(post("/api/v1/work-orders/{id}/approve", workOrderId)
                        .param("approverId", approverId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void startDeniesDifferentDepartment() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        doDenyDepartment(departmentId);

        mockMvc.perform(post("/api/v1/work-orders/{id}/start", workOrderId))
                .andExpect(status().isForbidden());

        verify(service, never()).start(workOrderId);
    }

    @Test
    void completeDeniesDifferentDepartment() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        doDenyDepartment(departmentId);

        mockMvc.perform(post("/api/v1/work-orders/{id}/complete", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "done",
                                  "summary": "summary"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(service, never()).complete(eq(workOrderId), any(CompleteWorkOrderRequest.class));
    }

    @Test
    void closeAllowsSameDepartment() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(service.close(eq(workOrderId), any())).thenReturn(dto(workOrderId, departmentId));

        mockMvc.perform(post("/api/v1/work-orders/{id}/close", workOrderId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "result": "closed",
                                  "closureNotes": "notes"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void contractorIdDoesNotBypassDepartmentScopeWithoutUserMapping() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, departmentId);
        workOrder.setContractorId(UUID.randomUUID());
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        doDenyDepartment(departmentId);

        mockMvc.perform(get("/api/v1/work-orders/{id}", workOrderId))
                .andExpect(status().isForbidden());
    }

    private void doDenyDepartment(UUID departmentId) {
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
    }

    private WorkOrder workOrder(UUID id, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setNumber("WO-2026-1001");
        workOrder.setTitle("Planned repair");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(departmentId);
        workOrder.setStatus(WorkOrderStatus.PLANNED);
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedById(UUID.randomUUID());
        return workOrder;
    }

    private WorkOrderDto dto(UUID id, UUID departmentId) {
        return new WorkOrderDto(
                id,
                "WO-2026-1001",
                "Planned repair",
                UUID.randomUUID(),
                departmentId,
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

    private String createPayload(UUID departmentId) {
        return """
                {
                  "number": "WO-2026-1001",
                  "title": "Planned repair",
                  "equipmentId": "%s",
                  "departmentId": "%s",
                  "type": "PLANNED",
                  "workType": "REPAIR",
                  "priority": "MEDIUM",
                  "createdById": "%s",
                  "summary": "summary"
                }
                """.formatted(UUID.randomUUID(), departmentId, UUID.randomUUID());
    }
}

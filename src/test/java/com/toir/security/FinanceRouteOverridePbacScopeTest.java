package com.toir.security;

import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideCreateRequest;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.ActualCostReviewRouteOverride;
import com.toir.exception.RestException;
import com.toir.mapper.ActualCostReviewRouteOverrideResponseMapper;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.actualCost.ActualCostReviewRouteOverrideRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.service.ActualCostReviewRouteOverrideService;
import com.toir.service.FinanceScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceRouteOverridePbacScopeTest {

    ActualCostReviewRouteOverrideRepository repository;
    ActualCostRepository actualCostRepository;
    ActualCostReviewRouteOverrideResponseMapper responseMapper;
    WorkOrderRepository workOrderRepository;
    RepairRequestRepository repairRequestRepository;
    BudgetLineRepository budgetLineRepository;
    ScopeAccessService scopeAccessService;
    ActualCostReviewRouteOverrideService service;

    @BeforeEach
    void setUp() {
        repository = mock(ActualCostReviewRouteOverrideRepository.class);
        actualCostRepository = mock(ActualCostRepository.class);
        responseMapper = mock(ActualCostReviewRouteOverrideResponseMapper.class);
        workOrderRepository = mock(WorkOrderRepository.class);
        repairRequestRepository = mock(RepairRequestRepository.class);
        budgetLineRepository = mock(BudgetLineRepository.class);
        scopeAccessService = mock(ScopeAccessService.class);
        FinanceScopeService financeScopeService = new FinanceScopeService(
                scopeAccessService,
                actualCostRepository,
                workOrderRepository,
                repairRequestRepository,
                budgetLineRepository
        );
        service = new ActualCostReviewRouteOverrideService(
                repository,
                actualCostRepository,
                responseMapper,
                financeScopeService
        );
    }

    @Test
    void findByActualCostRequiresActualCostScope() {
        UUID actualCostId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ActualCost actualCost = actualCost(actualCostId, workOrderId);
        when(actualCostRepository.findByIdAndIsDeletedFalse(actualCostId)).thenReturn(Optional.of(actualCost));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.findByActualCost(actualCostId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).findAllByActualCostIdAndIsDeletedFalseOrderByCreatedAtDesc(actualCostId);
    }

    @Test
    void activeListFiltersOutForbiddenOverrides() {
        UUID allowedDepartmentId = UUID.randomUUID();
        UUID forbiddenDepartmentId = UUID.randomUUID();
        ActualCostReviewRouteOverride allowed = override(UUID.randomUUID(), UUID.randomUUID(), allowedDepartmentId);
        ActualCostReviewRouteOverride forbidden = override(UUID.randomUUID(), UUID.randomUUID(), forbiddenDepartmentId);
        when(repository.findAllByActiveTrueAndIsDeletedFalse()).thenReturn(List.of(allowed, forbidden));
        when(scopeAccessService.canAccessDepartment(allowedDepartmentId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(forbiddenDepartmentId)).thenReturn(false);
        when(responseMapper.toResponse(any(), any())).thenReturn(null);

        var result = service.findActive();

        assertThat(result).hasSize(1);
    }

    @Test
    void applyRequiresActualCostScope() {
        UUID actualCostId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ActualCost actualCost = actualCost(actualCostId, workOrderId);
        when(actualCostRepository.findByIdAndIsDeletedFalse(actualCostId)).thenReturn(Optional.of(actualCost));
        when(workOrderRepository.findByIdAndIsDeletedFalse(workOrderId))
                .thenReturn(Optional.of(workOrder(workOrderId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.apply(new ActualCostReviewRouteOverrideCreateRequest(
                actualCostId,
                departmentId,
                "ECONOMIST",
                null,
                24,
                "Route change"
        ))).isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(ActualCostReviewRouteOverride.class));
    }

    @Test
    void deactivateMissingOverrideRemains404() {
        UUID overrideId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(overrideId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(overrideId, UUID.randomUUID(), "done"))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Route override not found");
    }

    @Test
    void deactivateForbiddenOverrideReturns403() {
        UUID overrideId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        ActualCostReviewRouteOverride override = override(overrideId, UUID.randomUUID(), departmentId);
        when(repository.findByIdAndIsDeletedFalse(overrideId)).thenReturn(Optional.of(override));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.deactivate(overrideId, UUID.randomUUID(), "done"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private ActualCost actualCost(UUID id, UUID workOrderId) {
        ActualCost actualCost = new ActualCost();
        actualCost.setId(id);
        actualCost.setWorkOrderId(workOrderId);
        actualCost.setCostCategoryId(UUID.randomUUID());
        actualCost.setAmount(100);
        return actualCost;
    }

    private WorkOrder workOrder(UUID id, UUID departmentId) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setDepartmentId(departmentId);
        return workOrder;
    }

    private ActualCostReviewRouteOverride override(UUID id, UUID actualCostId, UUID departmentId) {
        ActualCostReviewRouteOverride override = new ActualCostReviewRouteOverride();
        override.setId(id);
        override.setActualCostId(actualCostId);
        override.setDepartmentId(departmentId);
        override.setApprovalRoleCode("ECONOMIST");
        override.setComment("Route override");
        override.setActive(true);
        return override;
    }
}

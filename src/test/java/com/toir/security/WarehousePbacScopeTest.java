package com.toir.security;

import com.toir.dto.warehouse.WarehouseDto;
import com.toir.dto.warehouse.WarehouseRequest;
import com.toir.entity.warehouse.Warehouse;
import com.toir.exception.RestException;
import com.toir.repository.LocationRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WarehouseStockRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.service.WarehouseService;
import com.toir.service.warehouse.LegacyStockProjectionService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarehousePbacScopeTest {

    WarehouseRepository repository;
    WarehouseStockRepository stockRepository;
    DepartmentRepository departmentRepository;
    LocationRepository locationRepository;
    EmployeeRepository employeeRepository;
    AuditBuilderService auditBuilderService;
    ScopeAccessService scopeAccessService;
    LegacyStockProjectionService legacyStockProjectionService;
    WarehouseService service;

    @BeforeEach
    void setUp() {
        repository = mock(WarehouseRepository.class);
        stockRepository = mock(WarehouseStockRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        locationRepository = mock(LocationRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        auditBuilderService = mock(AuditBuilderService.class);
        scopeAccessService = mock(ScopeAccessService.class);
        legacyStockProjectionService = mock(LegacyStockProjectionService.class);
        when(legacyStockProjectionService.currentForWarehouse(any())).thenReturn(java.util.Map.of());
        service = new WarehouseService(
                repository,
                stockRepository,
                departmentRepository,
                locationRepository,
                employeeRepository,
                auditBuilderService,
                scopeAccessService,
                legacyStockProjectionService
        );
    }

    @Test
    void scopeAdminCanListAllWarehouses() {
        Warehouse first = warehouse(UUID.randomUUID(), UUID.randomUUID(), null);
        Warehouse second = warehouse(UUID.randomUUID(), UUID.randomUUID(), null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(repository.search(null, null, null, null, null)).thenReturn(List.of(first, second));
        when(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(first.getId())).thenReturn(List.of());
        when(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(second.getId())).thenReturn(List.of());

        List<WarehouseDto> result = service.findAll(null, null, null, null, null);

        assertThat(result).extracting(WarehouseDto::id).containsExactly(first.getId(), second.getId());
    }

    @Test
    void nonAdminListClampsDepartmentScope() {
        UUID requestedDepartmentId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        Warehouse allowed = warehouse(UUID.randomUUID(), currentDepartmentId, null);
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.enforceDepartmentScope(requestedDepartmentId)).thenReturn(currentDepartmentId);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(currentDepartmentId);
        when(scopeAccessService.canAccessDepartment(currentDepartmentId)).thenReturn(true);
        when(repository.search(null, currentDepartmentId, null, null, null)).thenReturn(List.of(allowed));
        when(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(allowed.getId())).thenReturn(List.of());

        List<WarehouseDto> result = service.findAll(null, requestedDepartmentId, null, null, null);

        assertThat(result).extracting(WarehouseDto::departmentId).containsExactly(currentDepartmentId);
        verify(repository).search(null, currentDepartmentId, null, null, null);
    }

    @Test
    void nonAdminWithoutDepartmentCannotReceiveGlobalWarehouses() {
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.enforceDepartmentScope(null)).thenReturn(null);
        when(scopeAccessService.currentDepartmentIdOrNull()).thenReturn(null);

        assertThatThrownBy(() -> service.findAll(null, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void detailAllowedByWarehouseDepartment() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, departmentId, null);
        when(repository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(stockRepository.findAllByWarehouseIdAndIsDeletedFalse(warehouseId)).thenReturn(List.of());

        WarehouseDto result = service.findById(warehouseId);

        assertThat(result.id()).isEqualTo(warehouseId);
    }

    @Test
    void detailForbiddenWarehouseReturns403() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, departmentId, null)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);
        when(scopeAccessService.canAccessEmployee(null)).thenReturn(false);

        assertThatThrownBy(() -> service.findById(warehouseId))
                .isInstanceOf(AccessDeniedException.class);

        verify(stockRepository, never()).findAllByWarehouseIdAndIsDeletedFalse(warehouseId);
    }

    @Test
    void missingWarehouseReturns404() {
        UUID warehouseId = UUID.randomUUID();
        when(repository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(warehouseId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Warehouse not found");
    }

    @Test
    void createWithForbiddenDepartmentReturns403() {
        UUID departmentId = UUID.randomUUID();
        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new WarehouseRequest(null, "Forbidden", departmentId, null, null, true)))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(any(Warehouse.class));
    }

    @Test
    void updateChecksCurrentAndTargetDepartmentScope() {
        UUID warehouseId = UUID.randomUUID();
        UUID currentDepartmentId = UUID.randomUUID();
        UUID targetDepartmentId = UUID.randomUUID();
        Warehouse existing = warehouse(warehouseId, currentDepartmentId, null);
        when(repository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(existing));
        when(scopeAccessService.canAccessDepartment(currentDepartmentId)).thenReturn(true);
        when(scopeAccessService.canAccessDepartment(targetDepartmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.update(warehouseId,
                new WarehouseRequest(null, "Moved", targetDepartmentId, null, null, true)))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(existing);
    }

    @Test
    void deleteForbiddenWarehouseReturns403() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Warehouse existing = warehouse(warehouseId, departmentId, null);
        when(repository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(existing));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(warehouseId))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).save(existing);
    }

    private Warehouse warehouse(UUID id, UUID departmentId, UUID responsibleId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setCode("WH-2026-0001");
        warehouse.setName("Warehouse");
        warehouse.setDepartmentId(departmentId);
        warehouse.setResponsibleId(responsibleId);
        warehouse.setActive(true);
        return warehouse;
    }
}

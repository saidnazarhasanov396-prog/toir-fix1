package com.toir.security;

import com.toir.dto.warehouse.WarehouseEquipmentAssignRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.exception.RestException;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.service.WarehouseEquipmentItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WarehouseEquipmentPbacScopeTest {

    WarehouseRepository warehouseRepository;
    EquipmentRepository equipmentRepository;
    DepartmentRepository departmentRepository;
    WarehouseEquipmentItemRepository itemRepository;
    ScopeAccessService scopeAccessService;
    WarehouseEquipmentItemService service;

    @BeforeEach
    void setUp() {
        warehouseRepository = mock(WarehouseRepository.class);
        equipmentRepository = mock(EquipmentRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        itemRepository = mock(WarehouseEquipmentItemRepository.class);
        scopeAccessService = mock(ScopeAccessService.class);
        service = new WarehouseEquipmentItemService(
                warehouseRepository,
                equipmentRepository,
                departmentRepository,
                itemRepository,
                scopeAccessService
        );
    }

    @Test
    void listByForbiddenWarehouseReturns403() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.list(warehouseId, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);

        verify(itemRepository, never()).findByWarehouseIdAndActiveTrueAndIsDeletedFalse(eq(warehouseId), any());
    }

    @Test
    void missingWarehouseStillReturns404ForList() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(warehouseId, null, 0, 20))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Warehouse not found");
    }

    @Test
    void listByAllowedWarehouseSucceeds() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(true);
        when(itemRepository.findByWarehouseIdAndActiveTrueAndIsDeletedFalse(eq(warehouseId), any()))
                .thenReturn(new PageImpl<>(List.of(item(warehouseId, UUID.randomUUID())), PageRequest.of(0, 20), 1));

        service.list(warehouseId, null, 0, 20);

        verify(itemRepository).findByWarehouseIdAndActiveTrueAndIsDeletedFalse(eq(warehouseId), any());
    }

    @Test
    void assignToForbiddenWarehouseReturns403() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.assign(warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.AVAILABLE)))
                .isInstanceOf(AccessDeniedException.class);

        verify(equipmentRepository, never()).findByIdAndIsDeletedFalse(equipmentId);
    }

    @Test
    void assignEquipmentFromForbiddenDepartmentReturns403() {
        UUID warehouseId = UUID.randomUUID();
        UUID warehouseDepartmentId = UUID.randomUUID();
        UUID equipmentDepartmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, equipmentDepartmentId);
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, warehouseDepartmentId)));
        when(scopeAccessService.canAccessDepartment(warehouseDepartmentId)).thenReturn(true);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(scopeAccessService.canAccessDepartment(equipmentDepartmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.assign(warehouseId,
                new WarehouseEquipmentAssignRequest(equipmentId, WarehouseEquipmentStatus.AVAILABLE)))
                .isInstanceOf(AccessDeniedException.class);

        verify(itemRepository, never()).save(any(WarehouseEquipmentItem.class));
    }

    @Test
    void statusUpdateInForbiddenWarehouseReturns403() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(itemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, equipmentId))
                .thenReturn(Optional.of(item(warehouseId, equipmentId)));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.updateStatus(warehouseId, equipmentId, WarehouseEquipmentStatus.AVAILABLE, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(itemRepository, never()).save(any(WarehouseEquipmentItem.class));
    }

    @Test
    void unassignForbiddenItemReturns403() {
        UUID warehouseId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        when(itemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, equipmentId))
                .thenReturn(Optional.of(item(warehouseId, equipmentId)));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse(warehouseId, departmentId)));
        when(scopeAccessService.canAccessDepartment(departmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.remove(warehouseId, equipmentId))
                .isInstanceOf(AccessDeniedException.class);

        verify(itemRepository, never()).save(any(WarehouseEquipmentItem.class));
    }

    private Warehouse warehouse(UUID id, UUID departmentId) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName("Warehouse");
        warehouse.setDepartmentId(departmentId);
        warehouse.setActive(true);
        return warehouse;
    }

    private WarehouseEquipmentItem item(UUID warehouseId, UUID equipmentId) {
        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setId(UUID.randomUUID());
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        item.setActive(true);
        return item;
    }

    private Equipment equipment(UUID id, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setDepartmentId(departmentId);
        return equipment;
    }
}

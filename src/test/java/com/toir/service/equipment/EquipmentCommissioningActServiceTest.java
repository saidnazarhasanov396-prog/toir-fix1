package com.toir.service.equipment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.equipmentcommissioning.EquipmentCommissioningActDto;
import com.toir.dto.equipmentcommissioning.EquipmentCommissioningActRequest;
import com.toir.entity.Department;
import com.toir.entity.StockMovement;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentCommissioningAct;
import com.toir.entity.users.Employee;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.*;
import com.toir.repository.*;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentCommissioningActRepository;
import com.toir.repository.equipment.EquipmentLocationHistoryRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.ApprovalService;
import com.toir.service.maintanance.MaintenanceAutomationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentCommissioningActServiceTest {

    @Mock EquipmentCommissioningActRepository repository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock WarehouseEquipmentItemRepository warehouseItemRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock LocationRepository locationRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock EquipmentLocationHistoryRepository locationHistoryRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock EquipmentStatusLifecycleService statusLifecycleService;
    @Mock MaintenanceAutomationService maintenanceAutomationService;
    @Mock ScopeAccessService scopeAccessService;
    @Mock ObjectProvider<ApprovalService> approvalServiceProvider;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks EquipmentCommissioningActService service;

    @Test
    void creationRejectsASecondOpenActForTheSameEquipment() {
        UUID equipmentId = UUID.randomUUID();
        when(repository.existsByEquipmentIdAndStatusInAndIsDeletedFalse(
                eq(equipmentId), anyCollection())).thenReturn(true);

        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> service.create(new EquipmentCommissioningActRequest(
                equipmentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                "COMM-2026-003",
                today,
                today,
                today,
                null,
                null
        )))
                .hasMessageContaining("open commissioning act already exists")
                .hasMessageContaining(equipmentId.toString());

        verifyNoInteractions(equipmentRepository, warehouseItemRepository);
        verify(repository, never()).save(any());
    }

    @Test
    void creationResolvesTheSuppliedWarehouseItemByItsId() {
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();

        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        Department department = new Department();
        department.setId(departmentId);
        Employee employee = new Employee();
        employee.setId(responsibleId);
        employee.setActive(true);
        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setId(itemId);
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        item.setActive(true);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(warehouse));
        when(departmentRepository.findByIdAndIsDeletedFalse(departmentId)).thenReturn(Optional.of(department));
        when(employeeRepository.findByIdAndIsDeletedFalse(responsibleId)).thenReturn(Optional.of(employee));
        when(warehouseItemRepository.findByIdAndIsDeletedFalse(itemId)).thenReturn(Optional.of(item));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate today = LocalDate.now();
        EquipmentCommissioningActDto result = service.create(new EquipmentCommissioningActRequest(
                equipmentId,
                warehouseId,
                itemId,
                departmentId,
                null,
                responsibleId,
                "COMM-2026-002",
                today,
                today,
                today,
                null,
                null
        ));

        assertThat(result.warehouseItemId()).isEqualTo(itemId);
        verify(warehouseItemRepository).findByIdAndIsDeletedFalse(itemId);
        verify(warehouseItemRepository, never())
                .findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(any(), any());
    }

    @Test
    void approvalAtomicallyActivatesAndMovesWarehouseEquipment() {
        UUID actId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID responsibleId = UUID.randomUUID();

        EquipmentCommissioningAct act = act(actId, equipmentId, warehouseId, itemId, departmentId, locationId, responsibleId);
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("EQ-1");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setStatus(EquipmentStatus.STANDBY);
        equipment.setCurrentLocationType(EquipmentLocationType.WAREHOUSE);
        equipment.setCurrentWarehouseId(warehouseId);
        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setId(itemId);
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);
        item.setActive(true);

        when(repository.findByIdAndIsDeletedFalse(actId)).thenReturn(Optional.of(act));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(warehouseItemRepository.findByIdAndIsDeletedFalse(itemId)).thenReturn(Optional.of(item));
        when(stockMovementRepository.save(any())).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movement.setId(UUID.randomUUID());
            return movement;
        });

        service.finalizeApproval(actId, UUID.randomUUID());

        assertThat(act.getStatus()).isEqualTo(EquipmentCommissioningStatus.APPROVED);
        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.STANDBY);
        assertThat(equipment.getCurrentLocationType()).isEqualTo(EquipmentLocationType.DEPARTMENT);
        assertThat(equipment.getDepartmentId()).isEqualTo(departmentId);
        assertThat(equipment.getOperationStartDate()).isEqualTo(act.getOperationStartDate());
        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.INSTALLED);
        assertThat(item.isActive()).isFalse();
        verify(statusLifecycleService).recordSystemTransition(
                eq(equipmentId), eq(EquipmentStatus.ACTIVE), contains(act.getActNumber()),
                eq("EQUIPMENT_COMMISSIONING"), eq(actId));
        verify(maintenanceAutomationService).evaluateEquipment(
                equipmentId, MaintenanceTriggerSource.MANUAL_RECALCULATION);
        verify(warehouseItemRepository, never())
                .findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(any(), any());
    }

    @Test
    void rejectionChangesOnlyActState() {
        UUID actId = UUID.randomUUID();
        EquipmentCommissioningAct act = new EquipmentCommissioningAct();
        act.setId(actId);
        act.setStatus(EquipmentCommissioningStatus.PENDING_APPROVAL);
        when(repository.findByIdAndIsDeletedFalse(actId)).thenReturn(Optional.of(act));

        service.finalizeRejection(actId, "Not ready");

        assertThat(act.getStatus()).isEqualTo(EquipmentCommissioningStatus.REJECTED);
        assertThat(act.getRejectionReason()).isEqualTo("Not ready");
        verifyNoInteractions(equipmentRepository, warehouseItemRepository, stockMovementRepository,
                statusLifecycleService, maintenanceAutomationService);
    }

    private EquipmentCommissioningAct act(UUID actId,
                                           UUID equipmentId,
                                           UUID warehouseId,
                                           UUID itemId,
                                           UUID departmentId,
                                           UUID locationId,
                                           UUID responsibleId) {
        EquipmentCommissioningAct act = new EquipmentCommissioningAct();
        act.setId(actId);
        act.setEquipmentId(equipmentId);
        act.setSourceWarehouseId(warehouseId);
        act.setWarehouseItemId(itemId);
        act.setTargetDepartmentId(departmentId);
        act.setTargetLocationId(locationId);
        act.setResponsibleEmployeeId(responsibleId);
        act.setActNumber("COMM-2026-001");
        act.setActDate(LocalDate.now());
        act.setCommissionedAt(LocalDate.now());
        act.setOperationStartDate(LocalDate.now());
        act.setStatus(EquipmentCommissioningStatus.PENDING_APPROVAL);
        return act;
    }
}

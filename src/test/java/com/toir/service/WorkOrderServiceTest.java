package com.toir.service;

import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock
    WorkOrderRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    PprTaskRepository pprTaskRepository;

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    WarehouseRepository warehouseRepository;

    @Mock
    WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;

    @InjectMocks
    WorkOrderService service;

    @Test
    void createOldStyleWorkOrderWithoutWorkTypeShouldSucceedAndPersistRepair() {

        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });

        WorkOrderRequest request = request(WorkOrderType.PLANNED, null, null, null);

        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<com.toir.entity.maintenance.WorkOrder> captor = ArgumentCaptor.forClass(com.toir.entity.maintenance.WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWorkType()).isEqualTo(WorkType.REPAIR);
        assertThat(result.workType()).isEqualTo(WorkType.REPAIR);
    }

    @Test
    void createOldStyleWorkOrderWithoutWorkTypeAndWithoutReplacementFieldsShouldSucceed() {

        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });

        WorkOrderRequest request = request(WorkOrderType.PLANNED, null, null, null);

        mockSuccessfulCreateDependencies(request);

        WorkOrderDto result = service.create(request);

        assertThat(result.warehouseId()).isNull();
        assertThat(result.replacementEquipmentId()).isNull();
        assertThat(result.workType()).isEqualTo(WorkType.REPAIR);
    }

    @Test
    void createReplacementWorkOrderWithoutWarehouseIdShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, null, UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("warehouseId is required");
    }

    @Test
    void createReplacementWorkOrderWithoutReplacementEquipmentIdShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("replacementEquipmentId is required");
    }

    @Test
    void createReplacementWorkOrderWithEquipmentNotBelongingToWarehouseShouldFail() {
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(new Equipment()));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("does not belong to selected warehouse");
    }

    @Test
    void createReplacementWorkOrderWithNonAvailableEquipmentShouldFail() {
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(replacementEquipmentId);
        item.setActive(true);
        item.setStatus(WarehouseEquipmentStatus.RESERVED);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(new Equipment()));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE");
    }

    @Test
    void createReplacementWorkOrderWithAlreadyAssignedReplacementEquipmentShouldFail() {
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(replacementEquipmentId);
        item.setActive(true);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(new Equipment()));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(repository.existsActiveReplacementAssignment(eq(replacementEquipmentId), eq(WorkType.REPLACEMENT), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("already assigned to another active work order");
    }

    @Test
    void createValidReplacementWorkOrderShouldPersistSuccessfully() {
        when(repository.save(any(WorkOrder.class)))
                .thenAnswer(invocation -> {
                    WorkOrder workOrder = invocation.getArgument(0);
                    ReflectionTestUtils.setField(workOrder, "id", UUID.randomUUID());
                    return workOrder;
                });

        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPLACEMENT, warehouseId, replacementEquipmentId);

        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(replacementEquipmentId);
        item.setActive(true);
        item.setStatus(WarehouseEquipmentStatus.AVAILABLE);

        Equipment sourceEquipment = new Equipment();
        sourceEquipment.setId(request.equipmentId());
        sourceEquipment.setName("Source Equipment");

        Equipment replacementEquipment = new Equipment();
        replacementEquipment.setId(replacementEquipmentId);
        replacementEquipment.setName("Replacement Equipment");

        Department department = new Department();
        department.setId(request.departmentId());
        department.setName("Maintenance");

        when(warehouseRepository.findByIdAndIsDeletedFalse(warehouseId)).thenReturn(Optional.of(new Warehouse()));
        when(equipmentRepository.findByIdAndIsDeletedFalse(replacementEquipmentId)).thenReturn(Optional.of(replacementEquipment));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(repository.existsActiveReplacementAssignment(eq(replacementEquipmentId), eq(WorkType.REPLACEMENT), any()))
                .thenReturn(false);
        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(equipmentRepository.findById(request.equipmentId())).thenReturn(Optional.of(sourceEquipment));
        when(equipmentRepository.findById(replacementEquipmentId)).thenReturn(Optional.of(replacementEquipment));
        when(departmentRepository.findById(request.departmentId())).thenReturn(Optional.of(department));

        WorkOrderDto result = service.create(request);

        ArgumentCaptor<com.toir.entity.maintenance.WorkOrder> captor = ArgumentCaptor.forClass(com.toir.entity.maintenance.WorkOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWarehouseId()).isEqualTo(warehouseId);
        assertThat(captor.getValue().getReplacementEquipmentId()).isEqualTo(replacementEquipmentId);
        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(result.replacementEquipmentId()).isEqualTo(replacementEquipmentId);
        assertThat(result.replacementEquipmentName()).isEqualTo("Replacement Equipment");
    }

    @Test
    void createNonReplacementWorkOrderWithReplacementFieldsShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, WorkType.REPAIR, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be null when workType is not REPLACEMENT");
    }

    @Test
    void createWorkOrderWithNullWorkTypeAndReplacementFieldsShouldFail() {
        WorkOrderRequest request = request(WorkOrderType.PLANNED, null, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be null when workType is not REPLACEMENT");
    }

    private WorkOrderRequest request(WorkOrderType type, WorkType workType, UUID warehouseId, UUID replacementEquipmentId) {
        return new WorkOrderRequest(
                "WO-2026-REPL-1",
                "Replacement job",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                type,
                workType,
                warehouseId,
                replacementEquipmentId,
                PriorityLevel.MEDIUM,
                null,
                null,
                UUID.randomUUID(),
                "summary"
        );
    }

    private void mockSuccessfulCreateDependencies(WorkOrderRequest request) {
        Equipment sourceEquipment = new Equipment();
        sourceEquipment.setId(request.equipmentId());
        sourceEquipment.setName("Source Equipment");

        Department department = new Department();
        department.setId(request.departmentId());
        department.setName("Maintenance");

        when(repository.existsByNumberAndIsDeletedFalse(request.number())).thenReturn(false);
        when(equipmentRepository.findById(eq(request.equipmentId()))).thenReturn(Optional.of(sourceEquipment));
        when(departmentRepository.findById(eq(request.departmentId()))).thenReturn(Optional.of(department));
    }
}

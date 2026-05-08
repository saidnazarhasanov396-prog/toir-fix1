package com.toir.service;

import com.toir.dto.workorder.CloseWorkOrderRequest;
import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.warehouse.Warehouse;
import com.toir.entity.warehouse.WarehouseEquipmentItem;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.enums.WorkOrderStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void approveReplacementWorkOrderShouldReserveReplacementEquipment() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.AVAILABLE);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.approve(workOrderId, UUID.randomUUID());

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
    }

    @Test
    void approveReplacementWorkOrderWithReservedItemShouldSucceedWithoutStatusChange() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.RESERVED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.approve(workOrderId, UUID.randomUUID());

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
        verify(warehouseEquipmentItemRepository, never()).save(any(WarehouseEquipmentItem.class));
    }

    @Test
    void approveReplacementWorkOrderWithInstalledItemShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.INSTALLED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.approve(workOrderId, UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE or RESERVED");
    }

    @Test
    void approveReplacementWorkOrderWithOutOfServiceItemShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.DRAFT, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.OUT_OF_SERVICE);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.approve(workOrderId, UUID.randomUUID()))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE or RESERVED");
    }

    @Test
    void startApprovedReplacementWorkOrderShouldReserveReplacementEquipment() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.APPROVED, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.AVAILABLE);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.start(workOrderId);

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.RESERVED);
    }

    @Test
    void startFromDraftShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only approved work orders can be started");
    }

    @Test
    void startFromClosedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.CLOSED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only approved work orders can be started");
    }

    @Test
    void completeReplacementWorkOrderShouldSetReplacementEquipmentInstalled() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.IN_PROGRESS, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.RESERVED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary"));

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.INSTALLED);
    }

    @Test
    void completeFromApprovedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.complete(workOrderId, new CompleteWorkOrderRequest("done", "summary")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only in-progress work orders can be completed");
    }

    @Test
    void closeReplacementWorkOrderShouldSetReplacementEquipmentInstalled() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.COMPLETED, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.RESERVED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));
        when(warehouseEquipmentItemRepository.save(any(WarehouseEquipmentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(item.getStatus()).isEqualTo(WarehouseEquipmentStatus.INSTALLED);
    }

    @Test
    void closeFromDraftShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only completed work orders can be closed");
    }

    @Test
    void closeFromApprovedShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.APPROVED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));

        assertThatThrownBy(() -> service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes")))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Only completed work orders can be closed");
    }

    @Test
    void closeFromCompletedShouldSucceed() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.COMPLETED, null, null);
        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        WorkOrderDto result = service.close(workOrderId, new CloseWorkOrderRequest("closed", "notes"));

        assertThat(result.status()).isEqualTo(WorkOrderStatus.CLOSED);
    }

    @Test
    void nonReplacementLifecycleShouldNotChangeWarehouseEquipmentItemStatus() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPAIR, WorkOrderStatus.DRAFT, null, null);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(repository.save(any(WorkOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        stubLifecycleDtoLookups(workOrder);

        service.approve(workOrderId, UUID.randomUUID());

        verifyNoInteractions(warehouseEquipmentItemRepository);
    }

    @Test
    void startReplacementWorkOrderWithInvalidWarehouseEquipmentStateShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.APPROVED, warehouseId, replacementEquipmentId);
        WarehouseEquipmentItem item = warehouseItem(warehouseId, replacementEquipmentId, WarehouseEquipmentStatus.INSTALLED);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("must be AVAILABLE or RESERVED");
    }

    @Test
    void startReplacementWorkOrderWithMissingWarehouseEquipmentItemShouldFail() {
        UUID workOrderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID replacementEquipmentId = UUID.randomUUID();
        WorkOrder workOrder = lifecycleWorkOrder(workOrderId, WorkType.REPLACEMENT, WorkOrderStatus.APPROVED, warehouseId, replacementEquipmentId);

        when(repository.findByIdAndIsDeletedFalse(workOrderId)).thenReturn(Optional.of(workOrder));
        when(warehouseEquipmentItemRepository.findByWarehouseIdAndEquipmentIdAndActiveTrueAndIsDeletedFalse(warehouseId, replacementEquipmentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(workOrderId))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("Replacement equipment item not found in selected warehouse");
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

    private WorkOrder lifecycleWorkOrder(UUID id,
                                         WorkType workType,
                                         WorkOrderStatus status,
                                         UUID warehouseId,
                                         UUID replacementEquipmentId) {
        WorkOrder workOrder = new WorkOrder();
        ReflectionTestUtils.setField(workOrder, "id", id);
        workOrder.setNumber("WO-LIFE-" + id);
        workOrder.setTitle("Lifecycle test");
        workOrder.setEquipmentId(UUID.randomUUID());
        workOrder.setDepartmentId(UUID.randomUUID());
        workOrder.setType(WorkOrderType.PLANNED);
        workOrder.setWorkType(workType);
        workOrder.setStatus(status);
        workOrder.setCreatedById(UUID.randomUUID());
        workOrder.setWarehouseId(warehouseId);
        workOrder.setReplacementEquipmentId(replacementEquipmentId);
        return workOrder;
    }

    private WarehouseEquipmentItem warehouseItem(UUID warehouseId, UUID equipmentId, WarehouseEquipmentStatus status) {
        WarehouseEquipmentItem item = new WarehouseEquipmentItem();
        item.setWarehouseId(warehouseId);
        item.setEquipmentId(equipmentId);
        item.setStatus(status);
        item.setActive(true);
        item.setDeleted(false);
        return item;
    }

    private void stubLifecycleDtoLookups(WorkOrder workOrder) {
        when(equipmentRepository.findById(workOrder.getEquipmentId())).thenReturn(Optional.empty());
        when(departmentRepository.findById(workOrder.getDepartmentId())).thenReturn(Optional.empty());
        if (workOrder.getReplacementEquipmentId() != null) {
            when(equipmentRepository.findById(workOrder.getReplacementEquipmentId())).thenReturn(Optional.empty());
        }
    }
}

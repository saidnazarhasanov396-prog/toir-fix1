package com.toir.service.maintenanceworkspace;

import com.toir.dto.maintenanceworkspace.MaintenancePlannerBacklogItem;
import com.toir.dto.maintenanceworkspace.MaintenancePlannerCapacityResponse;
import com.toir.dto.maintenanceworkspace.MaintenanceWorkspaceFilter;
import com.toir.dto.workorder.WorkOrderMaterialReadinessDto;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.users.BrigadeMember;
import com.toir.enums.MaterialReadinessStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.repository.WorkOrderRepository;
import com.toir.service.WorkOrderMaterialReadinessService;
import com.toir.service.integration.ToirErpWorkOrderSnapshotPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenancePlannerServiceTest {

    @Mock
    WorkOrderRepository workOrderRepository;

    @Mock
    WorkOrderMaterialReadinessService materialReadinessService;

    @Mock
    ToirErpWorkOrderSnapshotPublisher erpWorkOrderDeltas;

    @Test
    void backlogFiltersItemsAndCapacityUsesFilteredBacklog() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        WorkOrder matchingReady = workOrder(
                departmentId,
                equipmentId,
                "WO-1",
                "Pump service",
                PriorityLevel.HIGH,
                WorkOrderStatus.PLANNED,
                Instant.parse("2026-07-10T08:00:00Z"),
                Instant.parse("2026-07-10T12:00:00Z"),
                true,
                true
        );
        WorkOrder blockedMaterial = workOrder(
                departmentId,
                equipmentId,
                "WO-2",
                "Pump service",
                PriorityLevel.HIGH,
                WorkOrderStatus.PLANNED,
                Instant.parse("2026-07-10T08:00:00Z"),
                Instant.parse("2026-07-10T12:00:00Z"),
                false,
                true
        );
        WorkOrder otherDepartment = workOrder(
                UUID.randomUUID(),
                equipmentId,
                "WO-3",
                "Pump service",
                PriorityLevel.HIGH,
                WorkOrderStatus.PLANNED,
                Instant.parse("2026-07-10T08:00:00Z"),
                Instant.parse("2026-07-10T12:00:00Z"),
                true,
                true
        );
        when(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(matchingReady, blockedMaterial, otherDepartment));
        readiness(matchingReady, MaterialReadinessStatus.READY, false);
        readiness(blockedMaterial, MaterialReadinessStatus.SHORTAGE, true);
        readiness(otherDepartment, MaterialReadinessStatus.READY, false);
        MaintenancePlannerService service = new MaintenancePlannerService(workOrderRepository, materialReadinessService, erpWorkOrderDeltas);
        MaintenanceWorkspaceFilter filter = new MaintenanceWorkspaceFilter(
                "pump",
                departmentId,
                equipmentId,
                "HIGH",
                "PLANNED",
                null,
                null,
                null,
                "READY",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-31T23:59:59Z")
        );

        List<MaintenancePlannerBacklogItem> backlog = service.backlog(filter);
        MaintenancePlannerCapacityResponse capacity = service.capacity(filter);

        assertThat(backlog).extracting("workOrderId").containsExactly(matchingReady.getId());
        assertThat(capacity.openWorkOrders()).isEqualTo(1);
        assertThat(capacity.scheduledWorkOrders()).isEqualTo(1);
        assertThat(capacity.unscheduledWorkOrders()).isZero();
    }

    @Test
    void materialReadinessAppliesFilterBeforeReadinessGate() {
        UUID departmentId = UUID.randomUUID();
        WorkOrder matchingBlocked = workOrder(
                departmentId,
                UUID.randomUUID(),
                "WO-1",
                "Blocked pump",
                PriorityLevel.MEDIUM,
                WorkOrderStatus.PLANNED,
                null,
                null,
                false,
                true
        );
        WorkOrder otherDepartmentBlocked = workOrder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "WO-2",
                "Blocked pump",
                PriorityLevel.MEDIUM,
                WorkOrderStatus.PLANNED,
                null,
                null,
                false,
                true
        );
        when(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(matchingBlocked, otherDepartmentBlocked));
        readiness(matchingBlocked, MaterialReadinessStatus.SHORTAGE, true);
        readiness(otherDepartmentBlocked, MaterialReadinessStatus.SHORTAGE, true);
        MaintenancePlannerService service = new MaintenancePlannerService(workOrderRepository, materialReadinessService, erpWorkOrderDeltas);

        List<MaintenancePlannerBacklogItem> items = service.materialReadiness(
                new MaintenanceWorkspaceFilter(null, departmentId, null, null, null, null, null, null, null, null, null)
        );

        assertThat(items).extracting("workOrderId").containsExactly(matchingBlocked.getId());
    }


    private void readiness(WorkOrder workOrder, MaterialReadinessStatus status, boolean blocking) {
        when(materialReadinessService.getReadiness(workOrder.getId()))
                .thenReturn(new WorkOrderMaterialReadinessDto(
                        workOrder.getId(),
                        workOrder.getEquipmentId(),
                        status,
                        blocking,
                        Instant.parse("2026-07-06T00:00:00Z"),
                        List.of()
                ));
    }

    private WorkOrder workOrder(
            UUID departmentId,
            UUID equipmentId,
            String number,
            String title,
            PriorityLevel priority,
            WorkOrderStatus status,
            Instant scheduledStart,
            Instant scheduledEnd,
            boolean hasWarehouse,
            boolean hasPerformer
    ) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setNumber(number);
        workOrder.setTitle(title);
        workOrder.setDepartmentId(departmentId);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setPriority(priority);
        workOrder.setStatus(status);
        workOrder.setStartPlannedAt(scheduledStart);
        workOrder.setEndPlannedAt(scheduledEnd);
        workOrder.setWarehouseId(hasWarehouse ? UUID.randomUUID() : null);
        if (hasPerformer) {
            BrigadeMember performer = new BrigadeMember();
            performer.setUserId(UUID.randomUUID());
            workOrder.setPerformer(performer);
        }
        return workOrder;
    }
}

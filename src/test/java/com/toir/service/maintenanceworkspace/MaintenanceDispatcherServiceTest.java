package com.toir.service.maintenanceworkspace;

import com.toir.dto.maintenanceworkspace.MaintenanceDispatcherQueues;
import com.toir.dto.maintenanceworkspace.MaintenanceWorkspaceFilter;
import com.toir.entity.defects.Defect;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.repair.RepairRequestRepository;
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
class MaintenanceDispatcherServiceTest {

    @Mock
    RepairRequestRepository repairRequestRepository;

    @Mock
    DefectRepository defectRepository;

    @Mock
    WorkOrderRepository workOrderRepository;

    @Test
    void queuesFiltersItemsAndRecomputesSummary() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        RepairRequest matchingEmergency = repairRequest(
                departmentId,
                equipmentId,
                "RR-1",
                "Pump seal leak",
                PriorityLevel.CRITICAL,
                RequestStatus.OPEN,
                Instant.parse("2026-07-10T09:00:00Z"),
                "line stopped"
        );
        RepairRequest otherDepartment = repairRequest(
                UUID.randomUUID(),
                equipmentId,
                "RR-2",
                "Pump seal leak",
                PriorityLevel.CRITICAL,
                RequestStatus.OPEN,
                Instant.parse("2026-07-10T09:00:00Z"),
                "line stopped"
        );
        Defect matchingTextButWrongType = defect(equipmentId, "DF-1", "Pump seal leak", "HIGH");
        WorkOrder matchingTextButWrongTypeWorkOrder = blockedWorkOrder(
                departmentId,
                equipmentId,
                "WO-1",
                "Pump seal leak",
                PriorityLevel.CRITICAL,
                WorkOrderStatus.SUSPENDED,
                Instant.parse("2026-07-10T09:00:00Z")
        );
        when(repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(matchingEmergency, otherDepartment));
        when(defectRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(matchingTextButWrongType));
        when(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .thenReturn(List.of(matchingTextButWrongTypeWorkOrder));

        MaintenanceDispatcherService service = new MaintenanceDispatcherService(
                repairRequestRepository,
                defectRepository,
                workOrderRepository
        );
        MaintenanceWorkspaceFilter filter = new MaintenanceWorkspaceFilter(
                "pump",
                departmentId,
                equipmentId,
                "CRITICAL",
                "OPEN",
                "REPAIR_REQUEST",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-31T23:59:59Z"),
                null,
                null,
                null
        );

        MaintenanceDispatcherQueues queues = service.queues(filter);

        assertThat(queues.summary().total()).isEqualTo(1);
        assertThat(queues.summary().emergencyRepairRequests()).isEqualTo(1);
        assertThat(queues.emergencyRepairRequests()).extracting("id").containsExactly(matchingEmergency.getId());
        assertThat(queues.newDefects()).isEmpty();
        assertThat(queues.blockedWorkOrders()).isEmpty();
    }

    private RepairRequest repairRequest(
            UUID departmentId,
            UUID equipmentId,
            String number,
            String title,
            PriorityLevel priority,
            RequestStatus status,
            Instant targetCompletionAt,
            String emergencyReason
    ) {
        RepairRequest request = new RepairRequest();
        request.setId(UUID.randomUUID());
        request.setNumber(number);
        request.setTitle(title);
        request.setDescription(title);
        request.setDepartmentId(departmentId);
        request.setEquipmentId(equipmentId);
        request.setReporterId(UUID.randomUUID());
        request.setAssignedToId(UUID.randomUUID());
        request.setPriority(priority);
        request.setStatus(status);
        request.setDetectedAt(targetCompletionAt.minusSeconds(3600));
        request.setTargetCompletionAt(targetCompletionAt);
        request.setEmergencyReason(emergencyReason);
        return request;
    }

    private Defect defect(UUID equipmentId, String code, String title, String severity) {
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID());
        defect.setCode(code);
        defect.setTitle(title);
        defect.setDescription(title);
        defect.setEquipmentId(equipmentId);
        defect.setSeverity(severity);
        defect.setStatus(DefectStatus.OPEN);
        defect.setDetectedAt(Instant.parse("2026-07-10T08:00:00Z"));
        return defect;
    }

    private WorkOrder blockedWorkOrder(
            UUID departmentId,
            UUID equipmentId,
            String number,
            String title,
            PriorityLevel priority,
            WorkOrderStatus status,
            Instant dueAt
    ) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setNumber(number);
        workOrder.setTitle(title);
        workOrder.setDepartmentId(departmentId);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setPriority(priority);
        workOrder.setStatus(status);
        workOrder.setCreatedAt(dueAt.minusSeconds(7200));
        workOrder.setEndPlannedAt(dueAt);
        return workOrder;
    }
}

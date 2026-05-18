package com.toir.repository;

import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.*;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairRequestStatsProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RepairRequestRepositoryStatsTest {

    @Autowired
    RepairRequestRepository repository;

    @Autowired
    WorkOrderRepository workOrderRepository;

    @Test
    void getRepairRequestStatsWithoutFiltersCountsAllNonDeletedRequests() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        RepairRequest openEmergencyWithWo = saveRepairRequest(
                "RR-001",
                "Pump emergency",
                "Emergency pump failure",
                departmentId,
                equipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY
        );

        RepairRequest openMedium = saveRepairRequest(
                "RR-002",
                "Pump leak",
                "Small leak",
                departmentId,
                equipmentId,
                RequestStatus.OPEN,
                PriorityLevel.MEDIUM
        );

        RepairRequest approvedEmergency = saveRepairRequest(
                "RR-003",
                "Motor issue",
                "Critical motor issue",
                departmentId,
                equipmentId,
                RequestStatus.APPROVED,
                PriorityLevel.EMERGENCY
        );

        saveRepairRequest(
                "RR-004",
                "Closed request",
                "Already closed",
                departmentId,
                equipmentId,
                RequestStatus.CLOSED,
                PriorityLevel.LOW
        );

        saveWorkOrder(openEmergencyWithWo.getId(), departmentId, equipmentId);
        saveWorkOrder(approvedEmergency.getId(), departmentId, equipmentId);

        RepairRequestStatsProjection stats = repository.getRepairRequestStats(
                null,
                null,
                null,
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        );

        assertThat(stats.getTotalRequests()).isEqualTo(4);
        assertThat(stats.getEmergency()).isEqualTo(2);
        assertThat(stats.getOpen()).isEqualTo(2);
        assertThat(stats.getWithWorkOrder()).isEqualTo(2);
    }

    @Test
    void getRepairRequestStatsWithDepartmentEquipmentAndSearchCountsOnlyMatchingRequests() {
        UUID targetDepartmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        UUID targetEquipmentId = UUID.randomUUID();
        UUID otherEquipmentId = UUID.randomUUID();

        RepairRequest targetOpen = saveRepairRequest(
                "RR-PUMP-001",
                "Pump overheating",
                "Pump is overheating",
                targetDepartmentId,
                targetEquipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY
        );

        RepairRequest targetApproved = saveRepairRequest(
                "RR-PUMP-002",
                "Pump vibration",
                "Pump vibration issue",
                targetDepartmentId,
                targetEquipmentId,
                RequestStatus.APPROVED,
                PriorityLevel.HIGH
        );

        RepairRequest otherDepartment = saveRepairRequest(
                "RR-PUMP-003",
                "Other department pump",
                "Pump issue in another department",
                otherDepartmentId,
                targetEquipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY
        );

        RepairRequest otherEquipment = saveRepairRequest(
                "RR-PUMP-004",
                "Other equipment pump",
                "Pump issue on another equipment",
                targetDepartmentId,
                otherEquipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY
        );

        RepairRequest nonMatchingSearch = saveRepairRequest(
                "RR-MOTOR-001",
                "Motor issue",
                "Motor issue",
                targetDepartmentId,
                targetEquipmentId,
                RequestStatus.OPEN,
                PriorityLevel.EMERGENCY
        );

        saveWorkOrder(targetOpen.getId(), targetDepartmentId, targetEquipmentId);
        saveWorkOrder(otherDepartment.getId(), otherDepartmentId, targetEquipmentId);
        saveWorkOrder(otherEquipment.getId(), targetDepartmentId, otherEquipmentId);
        saveWorkOrder(nonMatchingSearch.getId(), targetDepartmentId, targetEquipmentId);

        RepairRequestStatsProjection stats = repository.getRepairRequestStats(
                targetDepartmentId,
                targetEquipmentId,
                "%pump%",
                PriorityLevel.EMERGENCY.name(),
                RequestStatus.OPEN.name()
        );

        assertThat(stats.getTotalRequests()).isEqualTo(2);
        assertThat(stats.getEmergency()).isEqualTo(1);
        assertThat(stats.getOpen()).isEqualTo(1);
        assertThat(stats.getWithWorkOrder()).isEqualTo(1);
    }

    private RepairRequest saveRepairRequest(
            String number,
            String title,
            String description,
            UUID departmentId,
            UUID equipmentId,
            RequestStatus status,
            PriorityLevel priority
    ) {
        RepairRequest request = new RepairRequest();
        request.setId(UUID.randomUUID());
        request.setNumber(number);
        request.setTitle(title);
        request.setDescription(description);
        request.setDepartmentId(departmentId);
        request.setEquipmentId(equipmentId);
        request.setReporterId(UUID.randomUUID());
        request.setStatus(status);
        request.setPriority(priority);
        request.setDeleted(false);

        return repository.save(request);
    }

    private WorkOrder saveWorkOrder(
            UUID repairRequestId,
            UUID departmentId,
            UUID equipmentId
    ) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(UUID.randomUUID());
        workOrder.setNumber("WO-" + UUID.randomUUID());
        workOrder.setTitle("Work order for repair request");
        workOrder.setRepairRequestId(repairRequestId);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setDepartmentId(departmentId);
        workOrder.setStatus(WorkOrderStatus.APPROVED);
        workOrder.setType(WorkOrderType.values()[0]);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedById(UUID.randomUUID());
        workOrder.setDeleted(false);

        return workOrderRepository.save(workOrder);
    }
}
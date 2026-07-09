package com.toir.repository;

import com.toir.test.RepositorySliceTest;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RepositorySliceTest
class WorkOrderRepositoryStatsTest {

    @Autowired
    WorkOrderRepository repository;

    @Test
    void getWorkOrderStatsWithNoFiltersCountsAllOrders() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        saveWorkOrder("WO-001", departmentId, equipmentId, WorkOrderStatus.APPROVED, null);
        saveWorkOrder("WO-002", departmentId, equipmentId, WorkOrderStatus.IN_PROGRESS, null);
        saveWorkOrder("WO-003", departmentId, equipmentId, WorkOrderStatus.COMPLETED, null);
        saveWorkOrder("WO-004", departmentId, equipmentId, WorkOrderStatus.CLOSED, null);

        WorkOrderStatsProjection stats = repository.getWorkOrderStats(null, null, null, null);

        assertThat(stats.getTotalOrders()).isEqualTo(4);
        assertThat(stats.getOpenOrders()).isEqualTo(2);
        assertThat(stats.getCompletedOrders()).isEqualTo(2);
    }

    @Test
    void getWorkOrderStatsWithCompletedOrClosedScopeCountsOnlyFinalSuccessfulOrders() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        saveWorkOrder("WO-SCOPE-001", departmentId, equipmentId, WorkOrderStatus.APPROVED, null);
        saveWorkOrder("WO-SCOPE-002", departmentId, equipmentId, WorkOrderStatus.COMPLETED, null);
        saveWorkOrder("WO-SCOPE-003", departmentId, equipmentId, WorkOrderStatus.CLOSED, null);
        saveWorkOrder("WO-SCOPE-004", departmentId, equipmentId, WorkOrderStatus.CANCELLED, null);

        WorkOrderStatsProjection stats = repository.getWorkOrderStats(null, true, null, null, null);

        assertThat(stats.getTotalOrders()).isEqualTo(2);
        assertThat(stats.getOpenOrders()).isZero();
        assertThat(stats.getCompletedOrders()).isEqualTo(2);
    }

    @Test
    void getWorkOrderStatsWithUnplannedTypeScopeCountsOnlyEmergencyAndDefectOrders() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        saveWorkOrder("WO-TYPE-001", departmentId, equipmentId, WorkOrderStatus.APPROVED, null, WorkOrderType.EMERGENCY);
        saveWorkOrder("WO-TYPE-002", departmentId, equipmentId, WorkOrderStatus.APPROVED, null, WorkOrderType.DEFECT);
        saveWorkOrder("WO-TYPE-003", departmentId, equipmentId, WorkOrderStatus.APPROVED, null, WorkOrderType.PLANNED);

        WorkOrderStatsProjection stats = repository.getWorkOrderStats(null, false, null, null, null, null, true);

        assertThat(stats.getTotalOrders()).isEqualTo(2);
        assertThat(stats.getOpenOrders()).isEqualTo(2);
    }

    @Test
    void getWorkOrderStatsWithExactTypeCountsOnlyThatType() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        saveWorkOrder("WO-TYPE-EXACT-001", departmentId, equipmentId, WorkOrderStatus.APPROVED, null, WorkOrderType.EMERGENCY);
        saveWorkOrder("WO-TYPE-EXACT-002", departmentId, equipmentId, WorkOrderStatus.APPROVED, null, WorkOrderType.DEFECT);

        WorkOrderStatsProjection stats = repository.getWorkOrderStats(null, false, null, null, null, "DEFECT", false);

        assertThat(stats.getTotalOrders()).isEqualTo(1);
    }

    @Test
    void getWorkOrderStatsCountsOverdueOrders() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        // Overdue: active status + endPlannedAt in the past
        saveWorkOrder("WO-OD-001", departmentId, equipmentId, WorkOrderStatus.IN_PROGRESS,
                Instant.now().minus(1, ChronoUnit.DAYS));
        // Not overdue: no endPlannedAt
        saveWorkOrder("WO-OD-002", departmentId, equipmentId, WorkOrderStatus.IN_PROGRESS, null);
        // Not overdue: already completed
        saveWorkOrder("WO-OD-003", departmentId, equipmentId, WorkOrderStatus.COMPLETED,
                Instant.now().minus(1, ChronoUnit.DAYS));

        WorkOrderStatsProjection stats = repository.getWorkOrderStats(null, null, null, null);

        assertThat(stats.getOverdueOrders()).isEqualTo(1);
    }

    @Test
    void getWorkOrderStatsWithDepartmentFilterCountsOnlyThatDepartment() {
        UUID targetDept = UUID.randomUUID();
        UUID otherDept = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        saveWorkOrder("WO-D-001", targetDept, equipmentId, WorkOrderStatus.APPROVED, null);
        saveWorkOrder("WO-D-002", otherDept, equipmentId, WorkOrderStatus.APPROVED, null);

        WorkOrderStatsProjection stats = repository.getWorkOrderStats(null, targetDept, null, null);

        assertThat(stats.getTotalOrders()).isEqualTo(1);
    }

    @Test
    void getWorkOrderStatsWithSearchFilterCountsOnlyMatchingOrders() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        saveWorkOrder("WO-PUMP-001", departmentId, equipmentId, WorkOrderStatus.APPROVED, null);
        saveWorkOrder("WO-MOTOR-001", departmentId, equipmentId, WorkOrderStatus.APPROVED, null);

        WorkOrderStatsProjection stats = repository.getWorkOrderStats(null, null, null, "pump");

        assertThat(stats.getTotalOrders()).isEqualTo(1);
    }

    @Test
    void getWorkOrderStatsExcludesDeletedOrders() {
        UUID departmentId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();

        saveWorkOrder("WO-DEL-001", departmentId, equipmentId, WorkOrderStatus.APPROVED, null);
        WorkOrder deleted = saveWorkOrder("WO-DEL-002", departmentId, equipmentId, WorkOrderStatus.APPROVED, null);
        deleted.setDeleted(true);
        repository.save(deleted);

        WorkOrderStatsProjection stats = repository.getWorkOrderStats(null, null, null, null);

        assertThat(stats.getTotalOrders()).isEqualTo(1);
    }

    private WorkOrder saveWorkOrder(String number, UUID departmentId, UUID equipmentId,
                                    WorkOrderStatus status, Instant endPlannedAt) {
        return saveWorkOrder(number, departmentId, equipmentId, status, endPlannedAt, WorkOrderType.values()[0]);
    }

    private WorkOrder saveWorkOrder(String number, UUID departmentId, UUID equipmentId,
                                    WorkOrderStatus status, Instant endPlannedAt, WorkOrderType type) {
        WorkOrder wo = new WorkOrder();
        wo.setNumber(number);
        wo.setTitle("Work order " + number);
        wo.setEquipmentId(equipmentId);
        wo.setDepartmentId(departmentId);
        wo.setStatus(status);
        wo.setType(type);
        wo.setWorkType(WorkType.REPAIR);
        wo.setPriority(PriorityLevel.MEDIUM);
        wo.setCreatedById(UUID.randomUUID());
        wo.setEndPlannedAt(endPlannedAt);
        wo.setDeleted(false);
        return repository.save(wo);
    }
}

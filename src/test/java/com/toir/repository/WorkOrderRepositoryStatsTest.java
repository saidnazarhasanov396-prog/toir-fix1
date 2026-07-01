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
        WorkOrder wo = new WorkOrder();
        wo.setNumber(number);
        wo.setTitle("Work order " + number);
        wo.setEquipmentId(equipmentId);
        wo.setDepartmentId(departmentId);
        wo.setStatus(status);
        wo.setType(WorkOrderType.values()[0]);
        wo.setWorkType(WorkType.REPAIR);
        wo.setPriority(PriorityLevel.MEDIUM);
        wo.setCreatedById(UUID.randomUUID());
        wo.setEndPlannedAt(endPlannedAt);
        wo.setDeleted(false);
        return repository.save(wo);
    }
}

package com.toir.repository;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.PriorityLevel;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class WorkOrderRepositoryEquipmentTypeDashboardTest {

    private static final List<WorkOrderStatus> ACTIVE_STATUSES = List.of(
            WorkOrderStatus.PLANNED,
            WorkOrderStatus.APPROVED,
            WorkOrderStatus.IN_PROGRESS,
            WorkOrderStatus.SUSPENDED
    );

    @Autowired
    WorkOrderRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void countsActiveWorkOrdersGroupedByEquipmentType() {
        UUID departmentId = UUID.randomUUID();
        EquipmentType pump = saveEquipmentType("PUMP", "Pump");
        Equipment pumpA = saveEquipment("EQ-P-001", "Pump A", pump.getId(), departmentId);
        Equipment pumpB = saveEquipment("EQ-P-002", "Pump B", pump.getId(), departmentId);

        saveWorkOrder("WO-P-001", departmentId, pumpA.getId(), WorkOrderStatus.APPROVED, false);
        saveWorkOrder("WO-P-002", departmentId, pumpA.getId(), WorkOrderStatus.APPROVED, false);
        saveWorkOrder("WO-P-003", departmentId, pumpB.getId(), WorkOrderStatus.IN_PROGRESS, false);
        saveWorkOrder("WO-P-004", departmentId, pumpB.getId(), WorkOrderStatus.CLOSED, false);

        List<WorkOrderEquipmentTypeCountProjection> rows =
                repository.countByEquipmentTypeForDashboard(departmentId, ACTIVE_STATUSES);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getEquipmentTypeId()).isEqualTo(pump.getId());
            assertThat(row.getEquipmentTypeName()).isEqualTo("Pump");
            assertThat(row.getWorkOrderCount()).isEqualTo(3);
        });
    }

    @Test
    void excludesSoftDeletedWorkOrders() {
        UUID departmentId = UUID.randomUUID();
        EquipmentType pump = saveEquipmentType("PUMP-DEL", "Pump");
        Equipment equipment = saveEquipment("EQ-P-DEL", "Pump", pump.getId(), departmentId);

        saveWorkOrder("WO-ACTIVE", departmentId, equipment.getId(), WorkOrderStatus.IN_PROGRESS, false);
        saveWorkOrder("WO-DELETED", departmentId, equipment.getId(), WorkOrderStatus.IN_PROGRESS, true);

        List<WorkOrderEquipmentTypeCountProjection> rows =
                repository.countByEquipmentTypeForDashboard(departmentId, ACTIVE_STATUSES);

        assertThat(rows).singleElement().satisfies(row ->
                assertThat(row.getWorkOrderCount()).isEqualTo(1));
    }

    @Test
    void departmentFilterChangesCounts() {
        UUID targetDepartmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        EquipmentType pump = saveEquipmentType("PUMP-DEPT", "Pump");
        Equipment targetPump = saveEquipment("EQ-P-DEPT-1", "Pump target", pump.getId(), targetDepartmentId);
        Equipment otherPump = saveEquipment("EQ-P-DEPT-2", "Pump other", pump.getId(), otherDepartmentId);

        saveWorkOrder("WO-DEPT-1", targetDepartmentId, targetPump.getId(), WorkOrderStatus.IN_PROGRESS, false);
        saveWorkOrder("WO-DEPT-2", otherDepartmentId, otherPump.getId(), WorkOrderStatus.IN_PROGRESS, false);

        List<WorkOrderEquipmentTypeCountProjection> rows =
                repository.countByEquipmentTypeForDashboard(targetDepartmentId, ACTIVE_STATUSES);

        assertThat(rows).singleElement().satisfies(row ->
                assertThat(row.getWorkOrderCount()).isEqualTo(1));
    }

    @Test
    void groupsEquipmentWithoutTypeUnderUnspecified() {
        UUID departmentId = UUID.randomUUID();
        EquipmentType temporaryType = saveEquipmentType("TEMP-TYPE", "Temporary");
        Equipment equipment = saveEquipment("EQ-NO-TYPE", "No type", temporaryType.getId(), departmentId);
        entityManager.flush();
        jdbcTemplate.update("alter table equipment alter column equipment_type_id drop not null");
        jdbcTemplate.update("update equipment set equipment_type_id = null where id = ?", equipment.getId());

        saveWorkOrder("WO-NO-TYPE", departmentId, equipment.getId(), WorkOrderStatus.IN_PROGRESS, false);

        List<WorkOrderEquipmentTypeCountProjection> rows =
                repository.countByEquipmentTypeForDashboard(departmentId, ACTIVE_STATUSES);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getEquipmentTypeId()).isNull();
            assertThat(row.getEquipmentTypeName()).isEqualTo("Unspecified");
            assertThat(row.getWorkOrderCount()).isEqualTo(1);
        });
    }

    private EquipmentType saveEquipmentType(String code, String name) {
        EquipmentType type = new EquipmentType();
        type.setCode(code);
        type.setName(name);
        type.setCategory("PRODUCTION_EQUIPMENT");
        return entityManager.persistAndFlush(type);
    }

    private Equipment saveEquipment(String code, String name, UUID equipmentTypeId, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setCode(code);
        equipment.setName(name);
        equipment.setInventoryNumber("INV-" + code);
        equipment.setEquipmentTypeId(equipmentTypeId);
        equipment.setDepartmentId(departmentId);
        equipment.setResponsibleDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return entityManager.persistAndFlush(equipment);
    }

    private WorkOrder saveWorkOrder(String number,
                                    UUID departmentId,
                                    UUID equipmentId,
                                    WorkOrderStatus status,
                                    boolean deleted) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setNumber(number);
        workOrder.setTitle("Work order " + number);
        workOrder.setEquipmentId(equipmentId);
        workOrder.setDepartmentId(departmentId);
        workOrder.setStatus(status);
        workOrder.setType(WorkOrderType.DEFECT);
        workOrder.setWorkType(WorkType.REPAIR);
        workOrder.setPriority(PriorityLevel.MEDIUM);
        workOrder.setCreatedById(UUID.randomUUID());
        workOrder.setDeleted(deleted);
        return entityManager.persistAndFlush(workOrder);
    }
}

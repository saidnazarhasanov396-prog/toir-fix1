package com.toir.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.toir.entity.BaseEntity;
import com.toir.entity.ConditionReading;
import com.toir.entity.Department;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.users.User;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.users.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DashboardDetailDevDataSeederTest {

    @Test
    void storesDetailedMaintenanceFactsInOwnerRepositories() throws Exception {
        DepartmentRepository departments = mock(DepartmentRepository.class);
        EquipmentTypeRepository equipmentTypes = mock(EquipmentTypeRepository.class);
        EquipmentRepository equipment = mock(EquipmentRepository.class);
        WorkOrderRepository workOrders = mock(WorkOrderRepository.class);
        ConditionReadingRepository readings = mock(ConditionReadingRepository.class);
        UserRepository users = mock(UserRepository.class);
        User admin = new User();
        admin.setId(UUID.randomUUID());

        when(departments.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(equipmentTypes.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(equipment.findByCodeAndIsDeletedFalse("DEV-TOIR-PUMP-101")).thenReturn(Optional.empty());
        when(workOrders.existsByNumberAndIsDeletedFalse("DEV-TOIR-WO-001")).thenReturn(false);
        when(readings.findAllByEquipmentIdAndIsDeletedFalseOrderByRecordedAtDesc(any())).thenReturn(List.of());
        when(users.findByUsernameAndIsDeletedFalse("admin")).thenReturn(Optional.of(admin));
        when(departments.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(equipmentTypes.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(equipment.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(workOrders.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(readings.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        new DashboardDetailDevDataSeeder(departments, equipmentTypes, equipment, workOrders, readings, users).run(null);

        ArgumentCaptor<Equipment> equipmentRow = ArgumentCaptor.forClass(Equipment.class);
        verify(equipment).save(equipmentRow.capture());
        assertThat(equipmentRow.getValue().getCode()).isEqualTo("DEV-TOIR-PUMP-101");
        assertThat(equipmentRow.getValue().getDaysOfResourceRemaining()).isEqualTo(94);

        ArgumentCaptor<WorkOrder> order = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrders).save(order.capture());
        assertThat(order.getValue().getNumber()).isEqualTo("DEV-TOIR-WO-001");
        assertThat(order.getValue().getEquipmentId()).isEqualTo(equipmentRow.getValue().getId());
        assertThat(order.getValue().getCreatedById()).isEqualTo(admin.getId());

        ArgumentCaptor<ConditionReading> reading = ArgumentCaptor.forClass(ConditionReading.class);
        verify(readings).save(reading.capture());
        assertThat(reading.getValue().getSeverity()).isEqualTo("WARN");
        assertThat(reading.getValue().getValue()).isEqualTo(6.8D);
    }

    private static <T extends BaseEntity> T withId(T row) {
        row.setId(UUID.randomUUID());
        return row;
    }
}

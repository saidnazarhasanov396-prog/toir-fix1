package com.toir.service;

import com.toir.dto.equipment.EquipmentUsageSessionStartRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentUsageSession;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.users.Employee;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.EquipmentUsageSessionStatus;
import com.toir.exception.RestException;
import com.toir.repository.EquipmentUsageSessionRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentUsageSessionServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    EquipmentUsageSessionRepository sessionRepository;
    @Mock
    EmployeeRepository employeeRepository;
    @Mock
    EquipmentMeterRepository equipmentMeterRepository;
    @Mock
    VehicleDetailsRepository vehicleDetailsRepository;
    @Mock
    MeterService meterService;

    @InjectMocks
    EquipmentUsageSessionService service;

    @Test
    void startStoresOperatorDepartmentSnapshotAndOpenStatus() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID issuedBy = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-06-19T04:05:00Z");
        Equipment equipment = equipment(equipmentId, departmentId, EquipmentCategory.PRODUCTION_EQUIPMENT);
        Employee operator = employee(operatorId, departmentId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(employeeRepository.findByIdAndIsDeletedFalse(operatorId)).thenReturn(Optional.of(operator));
        when(sessionRepository.existsByEquipmentIdAndStatusAndIsDeletedFalse(
                equipmentId,
                EquipmentUsageSessionStatus.OPEN
        )).thenReturn(false);
        when(sessionRepository.existsByOperatorEmployeeIdAndStatusAndIsDeletedFalse(
                operatorId,
                EquipmentUsageSessionStatus.OPEN
        )).thenReturn(false);
        when(sessionRepository.save(any(EquipmentUsageSession.class))).thenAnswer(invocation -> {
            EquipmentUsageSession session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });

        var response = service.start(
                equipmentId,
                new EquipmentUsageSessionStartRequest(operatorId, startedAt, null, null, null, null, "shift start"),
                issuedBy
        );

        ArgumentCaptor<EquipmentUsageSession> captor = ArgumentCaptor.forClass(EquipmentUsageSession.class);
        verify(sessionRepository).save(captor.capture());
        EquipmentUsageSession saved = captor.getValue();
        assertThat(saved.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(saved.getOperatorEmployeeId()).isEqualTo(operatorId);
        assertThat(saved.getDepartmentId()).isEqualTo(departmentId);
        assertThat(saved.getStartedAt()).isEqualTo(startedAt);
        assertThat(saved.getIssuedBy()).isEqualTo(issuedBy);
        assertThat(saved.getStatus()).isEqualTo(EquipmentUsageSessionStatus.OPEN);
        assertThat(response.operatorEmployeeId()).isEqualTo(operatorId);
        assertThat(response.operatorName()).isEqualTo("Operator Ali");
    }

    @Test
    void startRejectsEquipmentWithAlreadyOpenUsageSession() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, departmentId, EquipmentCategory.PRODUCTION_EQUIPMENT);
        Employee operator = employee(operatorId, departmentId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(employeeRepository.findByIdAndIsDeletedFalse(operatorId)).thenReturn(Optional.of(operator));
        when(sessionRepository.existsByEquipmentIdAndStatusAndIsDeletedFalse(
                equipmentId,
                EquipmentUsageSessionStatus.OPEN
        )).thenReturn(true);

        assertThatThrownBy(() -> service.start(
                equipmentId,
                new EquipmentUsageSessionStartRequest(operatorId, Instant.parse("2026-06-18T04:05:00Z"), null, null, null, null, null),
                UUID.randomUUID()
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("open usage session");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void startVehicleSessionSnapshotsAssignedDriverUsageDeadline() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID assignerId = UUID.randomUUID();
        UUID issuedBy = UUID.randomUUID();
        Instant startedAt = Instant.now().plus(Duration.ofDays(1));
        Equipment equipment = equipment(equipmentId, departmentId, EquipmentCategory.VEHICLE);
        Employee driver = employee(driverId, departmentId);
        VehicleDetails details = new VehicleDetails();
        details.setEquipmentId(equipmentId);
        details.setAssignedDriverId(driverId);
        details.setAssignedDriverUsageLimitMinutes(180);
        details.setAssignedDriverAssignedBy(assignerId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(employeeRepository.findByIdAndIsDeletedFalse(driverId)).thenReturn(Optional.of(driver));
        when(sessionRepository.existsByEquipmentIdAndStatusAndIsDeletedFalse(
                equipmentId,
                EquipmentUsageSessionStatus.OPEN
        )).thenReturn(false);
        when(sessionRepository.existsByOperatorEmployeeIdAndStatusAndIsDeletedFalse(
                driverId,
                EquipmentUsageSessionStatus.OPEN
        )).thenReturn(false);
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(sessionRepository.save(any(EquipmentUsageSession.class))).thenAnswer(invocation -> {
            EquipmentUsageSession session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });

        var response = service.start(
                equipmentId,
                new EquipmentUsageSessionStartRequest(driverId, startedAt, null, null, 1000.0, 200.0, "dispatch"),
                issuedBy
        );

        ArgumentCaptor<EquipmentUsageSession> captor = ArgumentCaptor.forClass(EquipmentUsageSession.class);
        verify(sessionRepository).save(captor.capture());
        EquipmentUsageSession saved = captor.getValue();
        assertThat(saved.getUsageLimitMinutes()).isEqualTo(180);
        assertThat(saved.getDueAt()).isEqualTo(startedAt.plusSeconds(180L * 60L));
        assertThat(saved.getAssignmentActorUserId()).isEqualTo(assignerId);
        assertThat(response.usageLimitMinutes()).isEqualTo(180);
        assertThat(response.dueAt()).isEqualTo(startedAt.plusSeconds(180L * 60L));
        assertThat(response.overdue()).isFalse();
        assertThat(response.overdueMinutes()).isZero();
    }

    private static Equipment equipment(UUID id, UUID departmentId, EquipmentCategory category) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-USAGE");
        equipment.setName("Usage asset");
        equipment.setInventoryNumber("INV-USAGE");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setResponsibleDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(category);
        return equipment;
    }

    private static Employee employee(UUID id, UUID departmentId) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setPersonnelNumber("EMP-001");
        employee.setFirstName("Ali");
        employee.setLastName("Operator");
        employee.setPosition("Operator");
        employee.setDepartmentId(departmentId);
        employee.setHireDate(LocalDate.of(2025, 1, 1));
        employee.setActive(true);
        return employee;
    }
}

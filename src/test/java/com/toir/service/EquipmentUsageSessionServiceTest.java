package com.toir.service;

import com.toir.dto.equipment.EquipmentUsageSessionStartRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentUsageSession;
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
        Instant startedAt = Instant.parse("2026-06-18T04:05:00Z");
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

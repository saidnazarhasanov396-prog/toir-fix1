package com.toir.service;

import com.toir.dto.vehicle.VehicleDrivingSessionReturnRequest;
import com.toir.dto.vehicle.VehicleDrivingSessionStartRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.equipment.VehicleDrivingSession;
import com.toir.entity.users.Employee;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleDrivingSessionStatus;
import com.toir.exception.RestException;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.VehicleDrivingSessionRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.EmployeeWorkRoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class VehicleDrivingSessionServiceTest {

    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    VehicleDetailsRepository vehicleDetailsRepository;
    @Mock
    VehicleDrivingSessionRepository sessionRepository;
    @Mock
    EmployeeRepository employeeRepository;
    @Mock
    EmployeeWorkRoleAssignmentRepository employeeWorkRoleAssignmentRepository;
    @Mock
    EquipmentMeterRepository equipmentMeterRepository;
    @Mock
    MeterService meterService;

    @InjectMocks
    VehicleDrivingSessionService service;

    @Test
    void startRejectsDriverWhoIsNotAssignedToVehicle() {
        UUID equipmentId = UUID.randomUUID();
        UUID assignedDriverId = UUID.randomUUID();
        UUID otherDriverId = UUID.randomUUID();
        Equipment equipment = vehicle(equipmentId, EquipmentStatus.ACTIVE);
        VehicleDetails details = details(equipmentId, assignedDriverId);
        Employee driver = employee(otherDriverId, equipment.getDepartmentId(), true);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(employeeRepository.findByIdAndIsDeletedFalse(otherDriverId)).thenReturn(Optional.of(driver));
        when(employeeWorkRoleAssignmentRepository.existsActiveByEmployeeIdAndWorkRoleCode(otherDriverId, "DRIVER"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.start(
                equipmentId,
                new VehicleDrivingSessionStartRequest(otherDriverId, Instant.parse("2026-06-16T06:00:00Z"), null, null, "dispatch"),
                UUID.randomUUID()
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("assigned driver");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void returnClosesOpenSessionAndUpdatesVehicleDetails() {
        UUID equipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID returnedBy = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-06-16T06:00:00Z");
        Instant returnedAt = Instant.parse("2026-06-16T08:30:00Z");
        Equipment equipment = vehicle(equipmentId, EquipmentStatus.ACTIVE);
        VehicleDetails details = details(equipmentId, driverId);
        details.setCurrentOdometerKm(1000.0);
        details.setCurrentEngineHours(200.0);

        VehicleDrivingSession session = new VehicleDrivingSession();
        session.setId(UUID.randomUUID());
        session.setEquipmentId(equipmentId);
        session.setDriverEmployeeId(driverId);
        session.setStartedAt(startedAt);
        session.setStartOdometerKm(1000.0);
        session.setStartEngineHours(200.0);
        session.setStatus(VehicleDrivingSessionStatus.OPEN);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(sessionRepository.findByIdAndEquipmentIdAndIsDeletedFalse(session.getId(), equipmentId))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any(VehicleDrivingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentMeterRepository.findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(equipmentId))
                .thenReturn(java.util.List.of());

        var response = service.returnVehicle(
                equipmentId,
                session.getId(),
                new VehicleDrivingSessionReturnRequest(returnedAt, 1_125.0, 225.0, "returned clean"),
                returnedBy
        );

        assertThat(response.status()).isEqualTo(VehicleDrivingSessionStatus.RETURNED);
        assertThat(response.durationMinutes()).isEqualTo(150);
        assertThat(response.odometerDeltaKm()).isEqualTo(125.0);
        assertThat(response.engineHoursDelta()).isEqualTo(25.0);
        assertThat(details.getCurrentOdometerKm()).isEqualTo(1_125.0);
        assertThat(details.getCurrentEngineHours()).isEqualTo(225.0);
        verify(sessionRepository).save(session);
        verify(vehicleDetailsRepository).save(details);
    }

    private static Equipment vehicle(UUID equipmentId, EquipmentStatus status) {
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("VH-001");
        equipment.setName("Truck");
        equipment.setInventoryNumber("INV-VH-001");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(UUID.randomUUID());
        equipment.setStatus(status);
        equipment.setCategory(EquipmentCategory.VEHICLE);
        return equipment;
    }

    private static VehicleDetails details(UUID equipmentId, UUID driverId) {
        VehicleDetails details = new VehicleDetails();
        details.setId(UUID.randomUUID());
        details.setEquipmentId(equipmentId);
        details.setAssignedDriverId(driverId);
        details.setCurrentOdometerKm(1000.0);
        details.setCurrentEngineHours(200.0);
        return details;
    }

    private static Employee employee(UUID employeeId, UUID departmentId, boolean active) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setPersonnelNumber("EMP-DR-001");
        employee.setFirstName("Ali");
        employee.setLastName("Driver");
        employee.setPosition("Driver");
        employee.setDepartmentId(departmentId);
        employee.setHireDate(LocalDate.of(2025, 1, 1));
        employee.setActive(active);
        return employee;
    }
}

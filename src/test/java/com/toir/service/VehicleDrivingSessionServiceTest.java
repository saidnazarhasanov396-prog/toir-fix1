package com.toir.service;

import com.toir.dto.equipment.EquipmentUsageSessionResponse;
import com.toir.dto.equipment.EquipmentUsageSessionReturnRequest;
import com.toir.dto.equipment.EquipmentUsageSessionStartRequest;
import com.toir.dto.vehicle.VehicleDrivingSessionReturnRequest;
import com.toir.dto.vehicle.VehicleDrivingSessionStartRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.equipment.VehicleDrivingSession;
import com.toir.entity.users.Employee;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.EquipmentUsageSessionStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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
    EquipmentMeterRepository equipmentMeterRepository;
    @Mock
    MeterService meterService;
    @Mock
    EquipmentUsageSessionService equipmentUsageSessionService;
    @Mock
    EmployeeWorkRoleAssignmentRepository employeeWorkRoleAssignmentRepository;

    @InjectMocks
    VehicleDrivingSessionService service;

    @Test
    void startDelegatesToGenericUsageSessionService() {
        UUID equipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID issuedBy = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-06-16T06:00:00Z");
        Equipment equipment = vehicle(equipmentId, EquipmentStatus.ACTIVE);
        VehicleDetails details = details(equipmentId, driverId);
        Employee driver = employee(driverId, equipment.getDepartmentId(), true);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(employeeRepository.findByIdAndIsDeletedFalse(driverId)).thenReturn(Optional.of(driver));
        lenient().when(sessionRepository.save(any(VehicleDrivingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(equipmentUsageSessionService.start(
                eq(equipmentId),
                argThat(request -> driverId.equals(request.operatorEmployeeId())
                        && startedAt.equals(request.startedAt())
                        && request.startOdometerKm().equals(details.getCurrentOdometerKm())
                        && request.startEngineHours().equals(details.getCurrentEngineHours())
                        && "dispatch".equals(request.note())),
                eq(issuedBy)
        )).thenReturn(new EquipmentUsageSessionResponse(
                UUID.randomUUID(),
                equipmentId,
                driverId,
                "Driver Ali",
                equipment.getDepartmentId(),
                startedAt,
                null,
                null,
                null,
                null,
                null,
                null,
                details.getCurrentOdometerKm(),
                null,
                null,
                details.getCurrentEngineHours(),
                null,
                null,
                EquipmentUsageSessionStatus.OPEN,
                issuedBy,
                null,
                "dispatch"
        ));

        var response = service.start(
                equipmentId,
                new VehicleDrivingSessionStartRequest(driverId, startedAt, null, null, "dispatch"),
                issuedBy
        );

        assertThat(response.driverEmployeeId()).isEqualTo(driverId);
        assertThat(response.status()).isEqualTo(VehicleDrivingSessionStatus.OPEN);
        verify(equipmentUsageSessionService).start(eq(equipmentId), any(EquipmentUsageSessionStartRequest.class), eq(issuedBy));
        verify(sessionRepository, never()).save(any());
    }

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
    void startRejectsEmployeeWithoutDriverWorkRoleWhenRoleRequirementEnabled() {
        UUID equipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Equipment equipment = vehicle(equipmentId, EquipmentStatus.ACTIVE);
        VehicleDetails details = details(equipmentId, driverId);
        Employee driver = employee(driverId, equipment.getDepartmentId(), true);

        ReflectionTestUtils.setField(service, "driverRoleRequired", true);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(employeeRepository.findByIdAndIsDeletedFalse(driverId)).thenReturn(Optional.of(driver));
        when(employeeWorkRoleAssignmentRepository.existsActiveByEmployeeIdAndWorkRoleCode(driverId, "DRIVER")).thenReturn(false);

        assertThatThrownBy(() -> service.start(
                equipmentId,
                new VehicleDrivingSessionStartRequest(driverId, Instant.parse("2026-06-16T06:00:00Z"), null, null, "dispatch"),
                UUID.randomUUID()
        ))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("DRIVER");

        verify(equipmentUsageSessionService, never()).start(any(), any(), any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void returnDelegatesToGenericUsageSessionService() {
        UUID equipmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID returnedBy = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-06-16T06:00:00Z");
        Instant returnedAt = Instant.parse("2026-06-16T08:30:00Z");
        Equipment equipment = vehicle(equipmentId, EquipmentStatus.ACTIVE);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(equipmentUsageSessionService.returnEquipment(
                eq(equipmentId),
                eq(sessionId),
                argThat(request -> returnedAt.equals(request.returnedAt())
                        && request.endOdometerKm().equals(1_125.0)
                        && request.endEngineHours().equals(225.0)
                        && "returned clean".equals(request.note())),
                eq(returnedBy)
        )).thenReturn(new EquipmentUsageSessionResponse(
                sessionId,
                equipmentId,
                driverId,
                "Driver Ali",
                equipment.getDepartmentId(),
                startedAt,
                returnedAt,
                150L,
                null,
                null,
                null,
                null,
                1000.0,
                1_125.0,
                125.0,
                200.0,
                225.0,
                25.0,
                EquipmentUsageSessionStatus.RETURNED,
                UUID.randomUUID(),
                returnedBy,
                "returned clean"
        ));

        var response = service.returnVehicle(
                equipmentId,
                sessionId,
                new VehicleDrivingSessionReturnRequest(returnedAt, 1_125.0, 225.0, "returned clean"),
                returnedBy
        );

        assertThat(response.status()).isEqualTo(VehicleDrivingSessionStatus.RETURNED);
        assertThat(response.durationMinutes()).isEqualTo(150);
        assertThat(response.odometerDeltaKm()).isEqualTo(125.0);
        assertThat(response.engineHoursDelta()).isEqualTo(25.0);
        verify(equipmentUsageSessionService).returnEquipment(eq(equipmentId), eq(sessionId), any(EquipmentUsageSessionReturnRequest.class), eq(returnedBy));
        verify(sessionRepository, never()).save(any());
        verify(vehicleDetailsRepository, never()).save(any());
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

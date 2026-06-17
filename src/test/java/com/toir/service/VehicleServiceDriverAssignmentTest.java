package com.toir.service;

import com.toir.dto.vehicle.VehicleRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.users.Employee;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleType;
import com.toir.exception.RestException;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.VehicleDocumentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.EmployeeWorkRoleAssignmentRepository;
import com.toir.security.SecurityScope;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.equipment.EquipmentAttributeService;
import com.toir.service.equipment.EquipmentManualAttributeService;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.file_management.FileService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class VehicleServiceDriverAssignmentTest {

    @Mock
    EquipmentRepository equipmentRepository;
    @Mock
    VehicleDetailsRepository vehicleDetailsRepository;
    @Mock
    EquipmentService equipmentService;
    @Mock
    AuditBuilderService auditBuilderService;
    @Mock
    FileService fileService;
    @Mock
    UploadedFileRepository uploadedFileRepository;
    @Mock
    SecurityScope securityScope;
    @Mock
    EquipmentAttributeService equipmentAttributeService;
    @Mock
    EquipmentManualAttributeService equipmentManualAttributeService;
    @Mock
    VehicleDocumentRepository vehicleDocumentRepository;
    @Mock
    AttachmentGroupService attachmentGroupService;
    @Mock
    EmployeeRepository employeeRepository;
    @Mock
    EmployeeWorkRoleAssignmentRepository employeeWorkRoleAssignmentRepository;

    @InjectMocks
    VehicleService service;

    @Test
    void updateRejectsAssignedDriverFromDifferentDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID vehicleDepartmentId = UUID.randomUUID();
        UUID driverDepartmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Equipment equipment = vehicle(equipmentId, vehicleDepartmentId);
        VehicleDetails details = details(equipmentId);
        Employee driver = employee(driverId, driverDepartmentId, true);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(employeeRepository.findByIdAndIsDeletedFalse(driverId)).thenReturn(Optional.of(driver));
        when(employeeWorkRoleAssignmentRepository.existsActiveByEmployeeIdAndWorkRoleCode(driverId, "DRIVER"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(equipmentId, request(vehicleDepartmentId, driverId, equipment.getEquipmentTypeId())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("same department");

        verify(vehicleDetailsRepository, never()).save(any());
    }

    @Test
    void updateRejectsEmployeeWithoutDriverWorkRole() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Equipment equipment = vehicle(equipmentId, departmentId);
        VehicleDetails details = details(equipmentId);
        Employee employee = employee(employeeId, departmentId, true);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(employeeRepository.findByIdAndIsDeletedFalse(employeeId)).thenReturn(Optional.of(employee));
        when(employeeWorkRoleAssignmentRepository.existsActiveByEmployeeIdAndWorkRoleCode(employeeId, "DRIVER"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.update(equipmentId, request(departmentId, employeeId, equipment.getEquipmentTypeId())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("DRIVER");

        verify(vehicleDetailsRepository, never()).save(any());
    }

    @Test
    void updateAcceptsActiveDriverInSameDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Equipment equipment = vehicle(equipmentId, departmentId);
        VehicleDetails details = details(equipmentId);
        Employee driver = employee(driverId, departmentId, true);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(employeeRepository.findByIdAndIsDeletedFalse(driverId)).thenReturn(Optional.of(driver));
        when(employeeWorkRoleAssignmentRepository.existsActiveByEmployeeIdAndWorkRoleCode(driverId, "DRIVER"))
                .thenReturn(true);
        when(vehicleDetailsRepository.existsAssignedDriverOnAnotherVehicle(driverId, equipmentId)).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDocumentRepository.findAllByEquipmentId(equipmentId)).thenReturn(java.util.List.of());

        var result = service.update(equipmentId, request(departmentId, driverId, equipment.getEquipmentTypeId()));

        assertThat(result.vehicleDetails().assignedDriverId()).isEqualTo(driverId);
        assertThat(equipment.getResponsibleId()).isEqualTo(driverId);
        verify(vehicleDetailsRepository).save(details);
    }

    private static Equipment vehicle(UUID equipmentId, UUID departmentId) {
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setCode("VH-001");
        equipment.setName("Truck");
        equipment.setInventoryNumber("INV-VH-001");
        equipment.setEquipmentTypeId(UUID.randomUUID());
        equipment.setDepartmentId(departmentId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCategory(EquipmentCategory.VEHICLE);
        return equipment;
    }

    private static VehicleDetails details(UUID equipmentId) {
        VehicleDetails details = new VehicleDetails();
        details.setId(UUID.randomUUID());
        details.setEquipmentId(equipmentId);
        details.setPlateNumber("01A123AA");
        details.setVehicleType(VehicleType.TRUCK);
        details.setCurrentOdometerKm(10_000.0);
        details.setCurrentEngineHours(400.0);
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

    private static VehicleRequest request(UUID departmentId, UUID driverId, UUID equipmentTypeId) {
        return new VehicleRequest(
                null,
                "Truck",
                "INV-VH-001",
                null,
                null,
                equipmentTypeId,
                departmentId,
                null,
                EquipmentStatus.ACTIVE,
                "01A123AA",
                null,
                null,
                "Kamaz",
                "6520",
                2024,
                VehicleType.TRUCK,
                null,
                null,
                null,
                "DIESEL",
                null,
                null,
                2,
                driverId,
                10_000.0,
                400.0,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}

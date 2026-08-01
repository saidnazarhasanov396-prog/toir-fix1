package com.toir.service;

import com.toir.dto.vehicle.VehicleRequest;
import com.toir.dto.equipment.EquipmentDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.VehicleDetails;
import com.toir.entity.users.Employee;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.VehicleType;
import com.toir.exception.RestException;
import com.toir.repository.MxikRepository;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.VehicleDetailsRepository;
import com.toir.repository.VehicleDocumentRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.EmployeeWorkRoleAssignmentRepository;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.equipment.EquipmentAttributeService;
import com.toir.service.equipment.EquipmentManualAttributeService;
import com.toir.service.equipment.EquipmentService;
import com.toir.service.file_management.FileService;
import com.toir.service.sparepartlifecycle.VehicleMeterProjectionGuard;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
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
    MxikRepository mxikRepository;
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
    @Mock
    VehicleMeterProjectionGuard vehicleMeterProjectionGuard;

    @InjectMocks
    VehicleService service;

    @Test
    void findByEquipmentIdUsesVehicleDetailsAsCanonicalDriverWhenEquipmentMirrorDiffers() {
        UUID equipmentId = UUID.randomUUID();
        UUID canonicalDriverId = UUID.randomUUID();
        UUID mirroredResponsibleId = UUID.randomUUID();
        Equipment equipment = vehicle(equipmentId, UUID.randomUUID());
        equipment.setResponsibleId(mirroredResponsibleId);
        VehicleDetails details = details(equipmentId);
        details.setAssignedDriverId(canonicalDriverId);

        when(equipmentService.findById(equipmentId)).thenReturn(EquipmentDto.from(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(vehicleDocumentRepository.findAllByEquipmentId(equipmentId)).thenReturn(List.of());
        when(equipmentAttributeService.findValues(equipmentId)).thenReturn(List.of());
        when(equipmentManualAttributeService.list(equipmentId)).thenReturn(List.of());

        var result = service.findByEquipmentId(equipmentId);

        assertThat(result.equipment().responsibleId()).isEqualTo(mirroredResponsibleId);
        assertThat(result.vehicleDetails().assignedDriverId()).isEqualTo(canonicalDriverId);
    }

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

        assertThatThrownBy(() -> service.update(equipmentId, request(vehicleDepartmentId, driverId, equipment.getEquipmentTypeId())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("same department");

        verify(vehicleDetailsRepository, never()).save(any());
    }

    @Test
    void updateAcceptsActiveEmployeeInSameDepartment() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Equipment equipment = vehicle(equipmentId, departmentId);
        VehicleDetails details = details(equipmentId);
        Employee driver = employee(driverId, departmentId, true);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(employeeRepository.findByIdAndIsDeletedFalse(driverId)).thenReturn(Optional.of(driver));
        when(vehicleDetailsRepository.existsAssignedDriverOnAnotherVehicle(driverId, equipmentId)).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDocumentRepository.findAllByEquipmentId(equipmentId)).thenReturn(java.util.List.of());

        var result = service.update(equipmentId, request(departmentId, driverId, equipment.getEquipmentTypeId()));

        assertThat(result.vehicleDetails().assignedDriverId()).isEqualTo(driverId);
        assertThat(equipment.getResponsibleId()).isEqualTo(driverId);
        verify(vehicleDetailsRepository).save(details);
    }

    @Test
    void updateStoresAssignedDriverUsageLimitAndAssignmentActor() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Equipment equipment = vehicle(equipmentId, departmentId);
        VehicleDetails details = details(equipmentId);
        Employee driver = employee(driverId, departmentId, true);

        when(securityScope.currentUser()).thenReturn(authenticatedUser(actorId));
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(employeeRepository.findByIdAndIsDeletedFalse(driverId)).thenReturn(Optional.of(driver));
        when(vehicleDetailsRepository.existsAssignedDriverOnAnotherVehicle(driverId, equipmentId)).thenReturn(false);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDetailsRepository.save(any(VehicleDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleDocumentRepository.findAllByEquipmentId(equipmentId)).thenReturn(java.util.List.of());

        var result = service.update(equipmentId, request(departmentId, driverId, equipment.getEquipmentTypeId(), 240));

        assertThat(result.vehicleDetails().assignedDriverId()).isEqualTo(driverId);
        assertThat(result.vehicleDetails().assignedDriverUsageLimitMinutes()).isEqualTo(240);
        assertThat(details.getAssignedDriverUsageLimitMinutes()).isEqualTo(240);
        assertThat(details.getAssignedDriverAssignedBy()).isEqualTo(actorId);
        assertThat(details.getAssignedDriverAssignedAt()).isNotNull();
    }

    @Test
    void updateRejectsUsageLimitWithoutAssignedDriver() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Equipment equipment = vehicle(equipmentId, departmentId);
        VehicleDetails details = details(equipmentId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));

        assertThatThrownBy(() -> service.update(equipmentId, request(departmentId, null, equipment.getEquipmentTypeId(), 120)))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("assignedDriverId");

        verify(vehicleDetailsRepository, never()).save(any());
    }

    @Test
    void updateRejectsEmployeeWithoutDriverWorkRoleWhenRoleRequirementEnabled() {
        UUID equipmentId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Equipment equipment = vehicle(equipmentId, departmentId);
        VehicleDetails details = details(equipmentId);
        Employee driver = employee(driverId, departmentId, true);

        ReflectionTestUtils.setField(service, "driverRoleRequired", true);
        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(vehicleDetailsRepository.findByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(details));
        when(employeeRepository.findByIdAndIsDeletedFalse(driverId)).thenReturn(Optional.of(driver));
        when(employeeWorkRoleAssignmentRepository.existsActiveByEmployeeIdAndWorkRoleCode(driverId, "DRIVER")).thenReturn(false);

        assertThatThrownBy(() -> service.update(equipmentId, request(departmentId, driverId, equipment.getEquipmentTypeId())))
                .isInstanceOf(RestException.class)
                .hasMessageContaining("DRIVER");

        verify(vehicleDetailsRepository, never()).save(any());
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
        return request(departmentId, driverId, equipmentTypeId, null);
    }

    private static VehicleRequest request(UUID departmentId, UUID driverId, UUID equipmentTypeId, Integer assignedDriverUsageLimitMinutes) {
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
                assignedDriverUsageLimitMinutes,
                10_000.0,
                400.0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                driverId,
                UUID.randomUUID(),
                LocalDate.of(2024, 1, 1)
        );
    }

    private static AuthenticatedUser authenticatedUser(UUID userId) {
        return new AuthenticatedUser(userId.toString(), "user", "user@example.com", "User", null, "USER", List.of());
    }
}

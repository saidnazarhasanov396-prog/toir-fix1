package com.toir.service.equipment;

import com.toir.dto.equipment.EquipmentDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.users.Employee;
import com.toir.enums.EquipmentCategory;
import com.toir.enums.EquipmentStatus;
import com.toir.repository.FileAssetRepository;
import com.toir.repository.LocationRepository;
import com.toir.repository.WarehouseEquipmentItemRepository;
import com.toir.repository.WarehouseRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.EquipmentUsageSessionRepository;
import com.toir.repository.UploadedFileRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentDocumentFileRepository;
import com.toir.repository.equipment.EquipmentDocumentRepository;
import com.toir.repository.equipment.EquipmentCommissioningActRepository;
import com.toir.repository.equipment.EquipmentLocationHistoryRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.service.attachment.AttachmentGroupService;
import com.toir.service.file_management.FileService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentResponsibleRefTest {

    @Mock EquipmentRepository repository;
    @Mock EquipmentCommissioningActRepository equipmentCommissioningActRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock LocationRepository locationRepository;
    @Mock EquipmentTypeRepository equipmentTypeRepository;
    @Mock EquipmentPassportRepository passportRepository;
    @Mock EquipmentAttributeDefinitionRepository attributeDefinitionRepository;
    @Mock EquipmentAttributeValueRepository attributeValueRepository;
    @Mock EquipmentMeterRepository equipmentMeterRepository;
    @Mock FileAssetRepository fileAssetRepository;
    @Mock EquipmentLocationHistoryRepository equipmentLocationHistoryRepository;
    @Mock EquipmentUsageSessionRepository equipmentUsageSessionRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock WarehouseEquipmentItemService warehouseEquipmentItemService;
    @Mock WarehouseEquipmentItemRepository warehouseEquipmentItemRepository;
    @Mock RepairRequestRepository repairRequestRepository;
    @Mock DefectRepository defectRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock DowntimeEventRepository downtimeEventRepository;
    @Mock EquipmentAttributeService equipmentAttributeService;
    @Mock EquipmentManualAttributeService equipmentManualAttributeService;
    @Mock EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    @Spy  EquipmentLocationValidator equipmentLocationValidator;
    @Mock AuditBuilderService auditBuilderService;
    @Mock FileService fileService;
    @Mock UploadedFileRepository uploadedFileRepository;
    @Mock EquipmentDocumentRepository equipmentDocumentRepository;
    @Mock EquipmentDocumentFileRepository equipmentDocumentFileRepository;
    @Mock UserRepository userRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock ScopeAccessService scopeAccessService;
    @Mock AttachmentGroupService attachmentGroupService;

    @InjectMocks
    EquipmentService service;

    // ── tests ──────────────────────────────────────────────────────────────────

    @Test
    void enrichPopulatesResponsibleRefFromEmployee() {
        UUID responsibleId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-RESP-1");
        equipment.setResponsibleId(responsibleId);

        Employee employee = employee(responsibleId, "EMP-001", "Valiyev", "Ali", "Akmalovich", "+998901112233");

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1));
        when(employeeRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(employee));

        Page<EquipmentDto> result = service.search(null, null, null, null, null, false, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        EquipmentDto dto = result.getContent().getFirst();
        assertThat(dto.responsibleId()).isEqualTo(responsibleId);
        assertThat(dto.responsible()).isNotNull();
        assertThat(dto.responsible().id()).isEqualTo(responsibleId);
        assertThat(dto.responsible().personnelNumber()).isEqualTo("EMP-001");
        assertThat(dto.responsible().fullName()).isEqualTo("Valiyev Ali Akmalovich");
        assertThat(dto.responsible().phone()).isEqualTo("+998901112233");
    }

    @Test
    void enrichRejectsUnresolvedResponsibleEmployeeIdentityWithoutUserFallback() {
        UUID responsibleId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-RESP-2");
        equipment.setResponsibleId(responsibleId);

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1));
        when(employeeRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.search(null, null, null, null, null, false, null, 0, 20))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(equipment.getId().toString())
                .hasMessageContaining(responsibleId.toString());

        verify(userRepository, never()).findByIdAndIsDeletedFalse(responsibleId);
    }

    @Test
    void enrichReturnsNullResponsibleWhenResponsibleIdIsNull() {
        Equipment equipment = equipment("EQ-RESP-3");

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1));

        Page<EquipmentDto> result = service.search(null, null, null, null, null, false, null, 0, 20);

        EquipmentDto dto = result.getContent().getFirst();
        assertThat(dto.responsibleId()).isNull();
        assertThat(dto.responsible()).isNull();
    }

    @Test
    void fullNameOmitsNullParts() {
        UUID responsibleId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-RESP-4");
        equipment.setResponsibleId(responsibleId);

        Employee employee = employee(responsibleId, "EMP-002", "Karimov", "Bobur", null, null);

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1));
        when(employeeRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(employee));

        EquipmentDto dto = service.search(null, null, null, null, null, false, null, 0, 20)
                .getContent().getFirst();

        assertThat(dto.responsible()).isNotNull();
        assertThat(dto.responsible().fullName()).isEqualTo("Karimov Bobur");
        assertThat(dto.responsible().phone()).isNull();
    }

    @Test
    void fullNameWithOnlyLastName() {
        UUID responsibleId = UUID.randomUUID();
        Equipment equipment = equipment("EQ-RESP-5");
        equipment.setResponsibleId(responsibleId);

        Employee employee = employee(responsibleId, "EMP-003", "Toshmatov", null, null, null);

        stubEnrichment();
        when(repository.search(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(equipment), PageRequest.of(0, 20), 1));
        when(employeeRepository.findAllByIdInAndIsDeletedFalse(anyCollection()))
                .thenReturn(List.of(employee));

        EquipmentDto dto = service.search(null, null, null, null, null, false, null, 0, 20)
                .getContent().getFirst();

        assertThat(dto.responsible().fullName()).isEqualTo("Toshmatov");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Equipment equipment(String code) {
        Equipment e = new Equipment();
        e.setId(UUID.randomUUID());
        e.setCode(code);
        e.setName("Equipment " + code);
        e.setInventoryNumber("INV-" + code);
        e.setEquipmentTypeId(UUID.randomUUID());
        e.setDepartmentId(UUID.randomUUID());
        e.setStatus(EquipmentStatus.ACTIVE);
        e.setCategory(EquipmentCategory.PRODUCTION_EQUIPMENT);
        return e;
    }

    private Employee employee(UUID id, String personnelNumber,
                               String lastName, String firstName, String middleName,
                               String phone) {
        Employee emp = new Employee();
        emp.setId(id);
        emp.setPersonnelNumber(personnelNumber);
        emp.setLastName(lastName);
        emp.setFirstName(firstName);
        emp.setMiddleName(middleName);
        emp.setPhone(phone);
        emp.setPosition("Engineer");
        emp.setHireDate(LocalDate.of(2025, 1, 1));
        emp.setActive(true);
        return emp;
    }

    private void stubEnrichment() {
        lenient().when(departmentRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        lenient().when(locationRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        lenient().when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        lenient().when(repository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        lenient().when(passportRepository.findAllByEquipmentIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        lenient().when(warehouseEquipmentItemRepository.findActiveByEquipmentIds(anyCollection())).thenReturn(List.of());
        lenient().when(attributeDefinitionRepository.findAllByEquipmentTypeIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        lenient().when(attributeValueRepository.findAllByEquipmentIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        lenient().when(equipmentMeterRepository.findAllByEquipmentIdInAndActiveTrueAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        lenient().when(employeeRepository.findAllByIdInAndIsDeletedFalse(anyCollection())).thenReturn(List.of());
        lenient().when(equipmentCommissioningActRepository.findEquipmentIdsWithStatuses(
                anyCollection(), anyCollection())).thenReturn(List.of());
    }
}

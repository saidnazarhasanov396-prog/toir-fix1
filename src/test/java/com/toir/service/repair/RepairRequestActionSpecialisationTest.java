package com.toir.service.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.repairrequest.RepairRequestRequest;
import com.toir.entity.maintenance.MaintenanceAction;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.repair.RepairRequestTemplateAction;
import com.toir.entity.users.EmployeeSpecialisation;
import com.toir.entity.users.User;
import com.toir.enums.CriticalityLevel;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestSource;
import com.toir.repository.LocationRepository;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceActionRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.repair.RepairRequestTemplateActionRepository;
import com.toir.repository.repair.RepairRequestTemplateRepository;
import com.toir.repository.users.EmployeeSpecialisationRepository;
import com.toir.repository.users.UserRepository;
import com.toir.security.ScopeAccessService;
import com.toir.service.MeterService;
import com.toir.service.NotificationService;
import com.toir.service.equipment.EquipmentStatusLifecycleService;
import com.toir.service.maintanance.EquipmentMaintenanceEffectiveRuleResolver;
import com.toir.service.maintanance.MaintenanceDueEventService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairRequestActionSpecialisationTest {

    @Mock RepairRequestRepository repository;
    @Mock EquipmentRepository equipmentRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock LocationRepository locationRepository;
    @Mock UserRepository userRepository;
    @Mock DefectRepository defectRepository;
    @Mock WorkOrderRepository workOrderRepository;
    @Mock AuditBuilderService auditBuilderService;
    @Mock ScopeAccessService scopeAccessService;
    @Mock NotificationService notificationService;
    @Mock EquipmentStatusLifecycleService equipmentStatusLifecycleService;
    @Mock MaintenanceTemplateRepository maintenanceTemplateRepository;
    @Mock MaintenanceOperationRepository maintenanceOperationRepository;
    @Mock MaintenanceActionRepository maintenanceActionRepository;
    @Mock RepairRequestTemplateRepository repairRequestTemplateRepository;
    @Mock RepairRequestTemplateActionRepository repairRequestTemplateActionRepository;
    @Mock MaintenanceCompletionAnchorRepository maintenanceCompletionAnchorRepository;
    @Mock EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;
    @Mock EquipmentMeterRepository equipmentMeterRepository;
    @Mock MeterReadingRepository meterReadingRepository;
    @Mock MeterService meterService;
    @Mock MaintenanceDueEventService maintenanceDueEventService;
    @Mock ObjectMapper objectMapper;
    @Mock EmployeeSpecialisationRepository employeeSpecialisationRepository;

    @InjectMocks
    RepairRequestService service;

    UUID equipmentId = UUID.randomUUID();
    UUID departmentId = UUID.randomUUID();
    UUID templateId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();
    UUID specialistId = UUID.randomUUID();
    UUID specialisationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(repairRequestTemplateRepository.findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(any()))
                .thenReturn(List.of());
        lenient().when(repairRequestTemplateActionRepository.findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(any()))
                .thenReturn(List.of());
        lenient().when(maintenanceOperationRepository.findAllByTemplateIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of());
        lenient().when(defectRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(any()))
                .thenReturn(List.of());
        lenient().when(workOrderRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(any()))
                .thenReturn(List.of());
        lenient().when(meterReadingRepository.findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(any()))
                .thenReturn(List.of());
        lenient().when(effectiveRuleResolver.resolveApplicable(any())).thenReturn(List.of());
    }

    @Test
    void createWithSpecialisationIdSavesItOnActionRow() {
        MaintenanceTemplate template = template(templateId);
        MaintenanceAction action = action(actionId);
        MaintenanceOperation operation = operation(operationId, action, templateId);
        User specialist = user(specialistId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        when(maintenanceTemplateRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(template));
        when(maintenanceOperationRepository.findAllByTemplateIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(operation));
        when(maintenanceActionRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(action));
        when(userRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(specialist));
        lenient().when(defectRepository.maxSequenceByCodePrefix(any())).thenReturn(0L);
        when(repository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        RepairRequest saved = repairRequest(equipmentId, departmentId);
        when(repository.save(any())).thenReturn(saved);

        RepairRequestRequest request = new RepairRequestRequest(
                "RR-2026-0001",
                "Pump vibration",
                "Excess vibration",
                null,
                null,
                null,
                equipmentId,
                departmentId,
                null,
                UUID.randomUUID(),
                PriorityLevel.HIGH,
                CriticalityLevel.HIGH,
                RequestSource.MANUAL,
                null,
                null,
                null,
                List.of(new RepairRequestRequest.TemplateSelectionRequest(
                        templateId,
                        List.of(new RepairRequestRequest.ActionSelectionRequest(
                                operationId,
                                actionId,
                                specialistId,
                                specialisationId,
                                null
                        ))
                ))
        );

        service.create(request);

        ArgumentCaptor<List<RepairRequestTemplateAction>> captor = ArgumentCaptor.forClass(List.class);
        verify(repairRequestTemplateActionRepository).saveAll(captor.capture());

        List<RepairRequestTemplateAction> savedActions = captor.getValue();
        assertThat(savedActions).hasSize(1);
        assertThat(savedActions.getFirst().getSpecialisationId()).isEqualTo(specialisationId);
    }

    @Test
    void createWithNullSpecialisationIdSavesNullOnActionRow() {
        MaintenanceTemplate template = template(templateId);
        MaintenanceAction action = action(actionId);
        MaintenanceOperation operation = operation(operationId, action, templateId);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId))
                .thenReturn(Optional.of(equipment(equipmentId, departmentId)));
        when(maintenanceTemplateRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(template));
        when(maintenanceOperationRepository.findAllByTemplateIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(operation));
        when(maintenanceActionRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(action));
        lenient().when(defectRepository.maxSequenceByCodePrefix(any())).thenReturn(0L);
        when(repository.existsByNumberAndIsDeletedFalse(any())).thenReturn(false);
        RepairRequest saved = repairRequest(equipmentId, departmentId);
        when(repository.save(any())).thenReturn(saved);

        RepairRequestRequest request = new RepairRequestRequest(
                "RR-2026-0001",
                "Pump vibration",
                "Excess vibration",
                null,
                null,
                null,
                equipmentId,
                departmentId,
                null,
                UUID.randomUUID(),
                PriorityLevel.HIGH,
                CriticalityLevel.HIGH,
                RequestSource.MANUAL,
                null,
                null,
                null,
                List.of(new RepairRequestRequest.TemplateSelectionRequest(
                        templateId,
                        List.of(new RepairRequestRequest.ActionSelectionRequest(
                                operationId,
                                actionId,
                                null,
                                null,
                                null
                        ))
                ))
        );

        service.create(request);

        ArgumentCaptor<List<RepairRequestTemplateAction>> captor = ArgumentCaptor.forClass(List.class);
        verify(repairRequestTemplateActionRepository).saveAll(captor.capture());

        assertThat(captor.getValue().getFirst().getSpecialisationId()).isNull();
    }

    @Test
    void actionReferencesIncludeSpecialisationFieldsWhenPresent() {
        UUID repairRequestId = UUID.randomUUID();
        RepairRequest repairRequest = repairRequest(equipmentId, departmentId);
        repairRequest.setId(repairRequestId);

        EmployeeSpecialisation spec = specialisation(specialisationId);
        RepairRequestTemplateAction actionRow = actionRow(repairRequestId, templateId, specialistId, specialisationId);

        when(repository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(repairRequestTemplateActionRepository
                .findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(repairRequestId))
                .thenReturn(List.of(actionRow));
        when(maintenanceTemplateRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(template(templateId)));
        User specialist = user(specialistId);
        when(userRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(specialist));
        when(employeeSpecialisationRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(spec));

        var dto = service.findById(repairRequestId);

        assertThat(dto.actionReferences()).hasSize(1);
        var ref = dto.actionReferences().getFirst();
        assertThat(ref.specialisationId()).isEqualTo(specialisationId);
        assertThat(ref.specialisationNameRu()).isEqualTo("Механик");
        assertThat(ref.specialisationNameEn()).isEqualTo("Mechanic");
        assertThat(ref.specialisationNameUz()).isEqualTo("Mexanik");
    }

    @Test
    void actionReferencesHaveNullSpecialisationWhenNotSet() {
        UUID repairRequestId = UUID.randomUUID();
        RepairRequest repairRequest = repairRequest(equipmentId, departmentId);
        repairRequest.setId(repairRequestId);

        RepairRequestTemplateAction actionRow = actionRow(repairRequestId, templateId, null, null);

        when(repository.findByIdAndIsDeletedFalse(repairRequestId)).thenReturn(Optional.of(repairRequest));
        when(repairRequestTemplateActionRepository
                .findAllByRepairRequest_IdAndIsDeletedFalseOrderBySequenceAsc(repairRequestId))
                .thenReturn(List.of(actionRow));
        when(maintenanceTemplateRepository.findAllByIdInAndIsDeletedFalse(any()))
                .thenReturn(List.of(template(templateId)));

        var dto = service.findById(repairRequestId);

        assertThat(dto.actionReferences()).hasSize(1);
        var ref = dto.actionReferences().getFirst();
        assertThat(ref.specialisationId()).isNull();
        assertThat(ref.specialisationNameRu()).isNull();
        assertThat(ref.specialisationNameEn()).isNull();
        assertThat(ref.specialisationNameUz()).isNull();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private com.toir.entity.equipment.Equipment equipment(UUID id, UUID deptId) {
        var e = new com.toir.entity.equipment.Equipment();
        e.setId(id);
        e.setName("Pump");
        e.setDepartmentId(deptId);
        return e;
    }

    private RepairRequest repairRequest(UUID equipmentId, UUID departmentId) {
        RepairRequest r = new RepairRequest();
        r.setId(UUID.randomUUID());
        r.setNumber("RR-2026-0001");
        r.setTitle("Pump vibration");
        r.setDescription("Excess vibration");
        r.setEquipmentId(equipmentId);
        r.setDepartmentId(departmentId);
        return r;
    }

    private MaintenanceTemplate template(UUID id) {
        MaintenanceTemplate t = new MaintenanceTemplate();
        t.setId(id);
        t.setCode("TPL-001");
        t.setName("Repair template");
        t.setEquipmentTypeId(UUID.randomUUID());
        t.setMaintenanceKind(MaintenanceKind.CURRENT_REPAIR);
        t.setActive(true);
        return t;
    }

    private MaintenanceAction action(UUID id) {
        MaintenanceAction a = new MaintenanceAction();
        a.setId(id);
        a.setCode("ACT-001");
        a.setName("Inspect pump");
        a.setActive(true);
        return a;
    }

    private MaintenanceOperation operation(UUID id, MaintenanceAction action, UUID templateId) {
        MaintenanceTemplate template = new MaintenanceTemplate();
        template.setId(templateId);
        MaintenanceOperation op = new MaintenanceOperation();
        op.setId(id);
        op.setTemplate(template);
        op.setAction(action);
        op.setName("Inspect pump seals");
        op.setSequence(1);
        return op;
    }

    private RepairRequestTemplateAction actionRow(UUID repairRequestId, UUID templateId,
                                                   UUID specialistId, UUID specialisationId) {
        RepairRequest rr = new RepairRequest();
        rr.setId(repairRequestId);
        RepairRequestTemplateAction row = new RepairRequestTemplateAction();
        row.setId(UUID.randomUUID());
        row.setRepairRequest(rr);
        row.setTemplateId(templateId);
        row.setSpecialistId(specialistId);
        row.setSpecialisationId(specialisationId);
        row.setSequence(1);
        row.setNameSnapshot("Inspect pump seals");
        return row;
    }

    private User user(UUID id) {
        User u = new User();
        u.setId(id);
        u.setFullName("Ali Valiyev");
        return u;
    }

    private EmployeeSpecialisation specialisation(UUID id) {
        EmployeeSpecialisation s = new EmployeeSpecialisation();
        s.setId(id);
        s.setNameRu("Механик");
        s.setNameEn("Mechanic");
        s.setNameUz("Mexanik");
        s.setActive(true);
        return s;
    }
}

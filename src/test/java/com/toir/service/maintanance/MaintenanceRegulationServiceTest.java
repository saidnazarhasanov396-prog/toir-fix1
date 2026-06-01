package com.toir.service.maintanance;

import com.toir.repository.equipment.EquipmentTypeRepository;

import com.toir.dto.maintenanceregulation.EquipmentWithRegulationsDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationAttributeConditionRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MeterType;
import com.toir.enums.MaintenanceRegulationConditionOperator;
import com.toir.enums.PeriodicityUnit;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceRegulationServiceTest {

    @Mock
    MaintenanceRegulationRepository repository;

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    MaintenanceOperationRepository operationRepository;

    @Mock
    AuditBuilderService auditBuilderService;

    @Mock
    EquipmentTypeRepository equipmentTypeRepository;

    @Mock
    EquipmentAttributeDefinitionRepository attributeDefinitionRepository;

    @Mock
    MaintenanceRegulationAttributeConditionRepository conditionRepository;

    @Mock
    MaintenanceTemplateRepository templateRepository;

    @InjectMocks
    MaintenanceRegulationService service;

    @Test
    void searchWithEquipmentTypeValidatesAndFilters() {
        UUID equipmentTypeId = UUID.randomUUID();
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), equipmentTypeId, "MR-2026-0001", true);
        when(equipmentTypeRepository.existsByIdAndIsDeletedFalse(equipmentTypeId)).thenReturn(true);
        when(repository.searchPaginated(eq(equipmentTypeId), eq(true), eq("PREVENTIVE"), eq("pump"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(regulation)));

        var page = service.search(0, 20, "pump", equipmentTypeId, true, "PREVENTIVE");

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().equipmentTypeId()).isEqualTo(equipmentTypeId);
        verify(repository).searchPaginated(eq(equipmentTypeId), eq(true), eq("PREVENTIVE"), eq("pump"), any());
    }

    @Test
    void searchWithUnknownEquipmentTypeReturns404() {
        UUID equipmentTypeId = UUID.randomUUID();
        when(equipmentTypeRepository.existsByIdAndIsDeletedFalse(equipmentTypeId)).thenReturn(false);

        assertThatThrownBy(() -> service.search(0, 20, null, equipmentTypeId, null, null))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(repository, never()).searchPaginated(any(), any(), any(), any(), any());
    }

    @Test
    void equipmentWithRegulationsGroupsRegulationsByEquipmentType() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentType type = new EquipmentType();
        type.setId(typeId);
        type.setName("Pump");
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, "MR-2026-0001", true);
        regulation.setTemplateId(templateId);
        MaintenanceTemplate template = template(templateId, typeId, MaintenanceKind.PREVENTIVE, true);
        MaintenanceOperation operation = new MaintenanceOperation();
        operation.setTemplate(template);
        operation.setRequiredSkill("Mechanic");
        operation.setSafetyNotes("Lockout");
        operation.setToolsRequired("Wrench");
        operation.setSparePartsRequired("Seal kit");
        operation.setConsumablesRequired("Grease");

        when(equipmentTypeRepository.existsByIdAndIsDeletedFalse(typeId)).thenReturn(true);
        when(equipmentRepository.findAllForMaintenanceRegulations(typeId)).thenReturn(List.of(equipment));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(typeId))).thenReturn(List.of(type));
        when(repository.findAllByEquipmentTypeIdInAndOptionalActive(Set.of(typeId), true)).thenReturn(List.of(regulation));
        when(operationRepository.findAllByTemplateIdInAndIsDeletedFalse(Set.of(templateId))).thenReturn(List.of(operation));

        var page = service.equipmentWithRegulations(typeId, true, null, null);

        assertThat(page.getContent()).hasSize(1);
        EquipmentWithRegulationsDto dto = page.getContent().getFirst();
        assertThat(dto.equipmentId()).isEqualTo(equipmentId);
        assertThat(dto.equipmentTypeName()).isEqualTo("Pump");
        assertThat(dto.regulations()).hasSize(1);
        assertThat(dto.regulations().getFirst().requiredSkill()).isEqualTo("Mechanic");
        assertThat(dto.regulations().getFirst().sparePartsRequired()).isEqualTo("Seal kit");
    }

    @Test
    void equipmentWithRegulationsRejectsPartialPagination() {
        assertThatThrownBy(() -> service.equipmentWithRegulations(null, null, 0, null))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Both page and size");
                });
    }

    @Test
    void createWithoutCodeGeneratesCode() {
        int year = Year.now().getValue();
        String codePrefix = "MR-" + year + "-";
        String expectedCode = "MR-" + year + "-0001";

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(MaintenanceRegulation.class))).thenAnswer(invocation -> {
            MaintenanceRegulation regulation = invocation.getArgument(0);
            regulation.setId(UUID.randomUUID());
            return regulation;
        });

        MaintenanceRegulationDto created = service.create(request(null));

        assertThat(created.code()).isEqualTo(expectedCode);
        verify(repository).maxSequenceByCodePrefix(codePrefix);

        ArgumentCaptor<MaintenanceRegulation> captor = ArgumentCaptor.forClass(MaintenanceRegulation.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(expectedCode);
    }

    @Test
    void createWithClientCodeReturns400() {
        assertThatThrownBy(() -> service.create(request("MR-2026-9999")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("code is generated by backend and must not be provided");
                });

        verify(repository, never()).maxSequenceByCodePrefix(anyString());
        verify(repository, never()).save(any(MaintenanceRegulation.class));
    }

    @Test
    void updateDoesNotAllowChangingCode() {
        UUID regulationId = UUID.randomUUID();
        MaintenanceRegulation existing = new MaintenanceRegulation();
        existing.setId(regulationId);
        existing.setCode("MR-2026-0001");
        existing.setName("Regulation");
        existing.setEquipmentTypeId(UUID.randomUUID());
        existing.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        existing.setNormativeLaborHours(2.0);
        existing.setPeriodicityUnit(PeriodicityUnit.MONTH);
        existing.setPeriodicityValue(1);
        existing.setRequiresShutdown(false);

        assertThatThrownBy(() -> service.update(regulationId, request("MR-2026-0002")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("code is generated by backend and must not be provided");
                });

        verify(repository, never()).save(any(MaintenanceRegulation.class));
    }

    @Test
    void generatedCodeIsUnique() {
        int year = Year.now().getValue();
        String codePrefix = "MR-" + year + "-";
        String firstCandidate = "MR-" + year + "-0001";
        String secondCandidate = "MR-" + year + "-0002";

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(firstCandidate)).thenReturn(true);
        when(repository.existsByCode(secondCandidate)).thenReturn(false);
        when(repository.save(any(MaintenanceRegulation.class))).thenAnswer(invocation -> {
            MaintenanceRegulation regulation = invocation.getArgument(0);
            regulation.setId(UUID.randomUUID());
            return regulation;
        });

        MaintenanceRegulationDto created = service.create(request(null));

        assertThat(created.code()).isEqualTo(secondCandidate);
        verify(repository).existsByCode(firstCandidate);
        verify(repository).existsByCode(secondCandidate);
    }

    @Test
    void duplicateCodeDoesNotReturn500() {
        int year = Year.now().getValue();
        String codePrefix = "MR-" + year + "-";
        String firstCandidate = "MR-" + year + "-0001";
        String secondCandidate = "MR-" + year + "-0002";

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(firstCandidate)).thenReturn(false);
        when(repository.existsByCode(secondCandidate)).thenReturn(false);
        when(repository.save(any(MaintenanceRegulation.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"maintenance_regulations_code_key\""))
                .thenAnswer(invocation -> {
                    MaintenanceRegulation regulation = invocation.getArgument(0);
                    regulation.setId(UUID.randomUUID());
                    return regulation;
                });

        MaintenanceRegulationDto created = service.create(request(null));

        assertThat(created.code()).isEqualTo(secondCandidate);
        verify(repository, times(2)).save(any(MaintenanceRegulation.class));
    }

    @Test
    void createWithAttributeConditionPersistsCondition() {
        int year = Year.now().getValue();
        String codePrefix = "MR-" + year + "-";
        String expectedCode = "MR-" + year + "-0001";
        UUID typeId = UUID.randomUUID();

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(MaintenanceRegulation.class))).thenAnswer(invocation -> {
            MaintenanceRegulation regulation = invocation.getArgument(0);
            regulation.setId(UUID.randomUUID());
            regulation.setEquipmentTypeId(typeId);
            return regulation;
        });
        when(attributeDefinitionRepository.existsActiveByEquipmentTypeIdAndKey(typeId, "motor_power")).thenReturn(true);
        when(conditionRepository.findAllByRegulationIdAndIsDeletedFalse(any())).thenReturn(List.of());

        MaintenanceRegulationDto created = service.create(requestWithCondition(null, typeId, "motor_power"));

        assertThat(created.code()).isEqualTo(expectedCode);
        ArgumentCaptor<Iterable<MaintenanceRegulationAttributeCondition>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(conditionRepository).saveAll(captor.capture());
        MaintenanceRegulationAttributeCondition condition =
                ((List<MaintenanceRegulationAttributeCondition>) captor.getValue()).getFirst();
        assertThat(condition.getAttributeKey()).isEqualTo("motor_power");
        assertThat(condition.getOperator()).isEqualTo(MaintenanceRegulationConditionOperator.GREATER_THAN);
        assertThat(condition.getValueNumber()).isEqualTo(50.0);
    }

    @Test
    void createWithCompatibleTemplateStoresTemplateId() {
        int year = Year.now().getValue();
        String codePrefix = "MR-" + year + "-";
        String expectedCode = "MR-" + year + "-0001";
        UUID typeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        MaintenanceTemplate template = template(templateId, typeId, MaintenanceKind.PREVENTIVE, true);

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(templateRepository.findByIdAndIsDeletedFalse(templateId)).thenReturn(Optional.of(template));
        when(repository.save(any(MaintenanceRegulation.class))).thenAnswer(invocation -> {
            MaintenanceRegulation regulation = invocation.getArgument(0);
            regulation.setId(UUID.randomUUID());
            return regulation;
        });

        MaintenanceRegulationDto created = service.create(requestWithTemplate(null, typeId, templateId, MaintenanceKind.PREVENTIVE));

        assertThat(created.templateId()).isEqualTo(templateId);
        assertThat(created.templateCode()).isEqualTo("MT-2026-0001");
        assertThat(created.templateName()).isEqualTo("Pump preventive template");
        ArgumentCaptor<MaintenanceRegulation> captor = ArgumentCaptor.forClass(MaintenanceRegulation.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTemplateId()).isEqualTo(templateId);
    }

    @Test
    void createWithTemplateForDifferentEquipmentTypeReturns400() {
        int year = Year.now().getValue();
        String expectedCode = "MR-" + year + "-0001";
        UUID typeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        when(repository.maxSequenceByCodePrefix("MR-" + year + "-")).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(templateRepository.findByIdAndIsDeletedFalse(templateId))
                .thenReturn(Optional.of(template(templateId, UUID.randomUUID(), MaintenanceKind.PREVENTIVE, true)));

        assertThatThrownBy(() -> service.create(requestWithTemplate(null, typeId, templateId, MaintenanceKind.PREVENTIVE)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Maintenance template equipment type must match regulation equipment type");
                });

        verify(repository, never()).save(any(MaintenanceRegulation.class));
    }

    @Test
    void createWithTemplateForDifferentMaintenanceKindReturns400() {
        int year = Year.now().getValue();
        String expectedCode = "MR-" + year + "-0001";
        UUID typeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        when(repository.maxSequenceByCodePrefix("MR-" + year + "-")).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(templateRepository.findByIdAndIsDeletedFalse(templateId))
                .thenReturn(Optional.of(template(templateId, typeId, MaintenanceKind.INSPECTION, true)));

        assertThatThrownBy(() -> service.create(requestWithTemplate(null, typeId, templateId, MaintenanceKind.PREVENTIVE)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Maintenance template kind must match regulation maintenance kind");
                });

        verify(repository, never()).save(any(MaintenanceRegulation.class));
    }

    @Test
    void createWithInactiveTemplateReturns400() {
        int year = Year.now().getValue();
        String expectedCode = "MR-" + year + "-0001";
        UUID typeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        when(repository.maxSequenceByCodePrefix("MR-" + year + "-")).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(templateRepository.findByIdAndIsDeletedFalse(templateId))
                .thenReturn(Optional.of(template(templateId, typeId, MaintenanceKind.PREVENTIVE, false)));

        assertThatThrownBy(() -> service.create(requestWithTemplate(null, typeId, templateId, MaintenanceKind.PREVENTIVE)))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Maintenance template must be active");
                });

        verify(repository, never()).save(any(MaintenanceRegulation.class));
    }

    @Test
    void createWithUnknownConditionAttributeReturns400() {
        int year = Year.now().getValue();
        String expectedCode = "MR-" + year + "-0001";
        UUID typeId = UUID.randomUUID();
        when(repository.maxSequenceByCodePrefix("MR-" + year + "-")).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(MaintenanceRegulation.class))).thenAnswer(invocation -> {
            MaintenanceRegulation regulation = invocation.getArgument(0);
            regulation.setId(UUID.randomUUID());
            regulation.setEquipmentTypeId(typeId);
            return regulation;
        });
        when(attributeDefinitionRepository.existsActiveByEquipmentTypeIdAndKey(typeId, "unknown_key")).thenReturn(false);
        when(conditionRepository.findAllByRegulationIdAndIsDeletedFalse(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(requestWithCondition(null, typeId, "unknown_key")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("Unknown equipment attribute key");
                });
    }

    @Test
    void findByIdIncludesAttributeConditions() {
        UUID regulationId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(regulationId);
        regulation.setCode("MR-2026-0001");
        regulation.setName("High-power pump regulation");
        regulation.setEquipmentTypeId(typeId);
        regulation.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        regulation.setNormativeLaborHours(4.0);
        regulation.setActive(true);
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setRequiresShutdown(false);

        MaintenanceRegulationAttributeCondition condition = new MaintenanceRegulationAttributeCondition();
        condition.setId(UUID.randomUUID());
        condition.setRegulationId(regulationId);
        condition.setAttributeKey("motor_power");
        condition.setOperator(MaintenanceRegulationConditionOperator.GREATER_THAN);
        condition.setValueNumber(50.0);

        when(repository.findByIdAndIsDeletedFalse(regulationId)).thenReturn(Optional.of(regulation));
        when(equipmentTypeRepository.findByIdAndIsDeletedFalse(typeId)).thenReturn(Optional.empty());
        when(conditionRepository.findAllByRegulationIdAndIsDeletedFalse(regulationId)).thenReturn(List.of(condition));

        MaintenanceRegulationDto dto = service.findById(regulationId);

        assertThat(dto.attributeConditions()).hasSize(1);
        assertThat(dto.attributeConditions().getFirst().attributeKey()).isEqualTo("motor_power");
        assertThat(dto.attributeConditions().getFirst().operator())
                .isEqualTo(MaintenanceRegulationConditionOperator.GREATER_THAN);
        assertThat(dto.attributeConditions().getFirst().valueNumber()).isEqualTo(50.0);
    }

    private MaintenanceRegulationRequest request(String code) {
        return new MaintenanceRegulationRequest(
                code,
                "Monthly pump regulation",
                "Regulation description",
                UUID.randomUUID(),
                null,
                MaintenanceKind.PREVENTIVE,
                3.0,
                true,
                PeriodicityUnit.MONTH,
                1,
                3,
                false,
                MeterType.CUSTOM,
                10.0
        );
    }

    private MaintenanceRegulationRequest requestWithCondition(String code, UUID typeId, String attributeKey) {
        return new MaintenanceRegulationRequest(
                code,
                "High-power pump regulation",
                "Only pumps above 50 kW",
                typeId,
                null,
                MaintenanceKind.PREVENTIVE,
                4.0,
                true,
                PeriodicityUnit.MONTH,
                1,
                3,
                false,
                MeterType.CUSTOM,
                10.0,
                List.of(new MaintenanceRegulationAttributeConditionRequest(
                        attributeKey,
                        MaintenanceRegulationConditionOperator.GREATER_THAN,
                        null,
                        50.0,
                        null,
                        null,
                        null
                ))
        );
    }

    private MaintenanceRegulationRequest requestWithTemplate(String code, UUID typeId, UUID templateId, MaintenanceKind kind) {
        return new MaintenanceRegulationRequest(
                code,
                "Monthly pump regulation",
                "Regulation description",
                typeId,
                templateId,
                kind,
                3.0,
                true,
                PeriodicityUnit.MONTH,
                1,
                3,
                false,
                MeterType.CUSTOM,
                10.0
        );
    }

    private MaintenanceTemplate template(UUID id, UUID typeId, MaintenanceKind kind, boolean active) {
        MaintenanceTemplate template = new MaintenanceTemplate();
        template.setId(id);
        template.setCode("MT-2026-0001");
        template.setName("Pump preventive template");
        template.setEquipmentTypeId(typeId);
        template.setMaintenanceKind(kind);
        template.setActive(active);
        return template;
    }

    private MaintenanceRegulation regulation(UUID id, UUID typeId, String code, boolean active) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        regulation.setId(id);
        regulation.setCode(code);
        regulation.setName("Monthly pump regulation");
        regulation.setDescription("Regulation description");
        regulation.setEquipmentTypeId(typeId);
        regulation.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        regulation.setNormativeLaborHours(3.0);
        regulation.setActive(active);
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setRequiresShutdown(false);
        return regulation;
    }

    private Equipment equipment(UUID id, UUID typeId) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setCode("EQ-2026-0001");
        equipment.setName("Pump A");
        equipment.setInventoryNumber("INV-1");
        equipment.setEquipmentTypeId(typeId);
        return equipment;
    }
}

package com.toir.service.maintanance;

import com.toir.repository.equipment.EquipmentTypeRepository;

import com.toir.dto.maintenanceregulation.EquipmentTypeWithRegulationsDto;
import com.toir.dto.maintenanceregulation.EquipmentWithRegulationsDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationAttributeConditionRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationSparePartRequirementRequest;
import com.toir.entity.SparePart;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceOperation;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import com.toir.entity.maintenance.MaintenanceRegulationSparePartRequirement;
import com.toir.entity.maintenance.MaintenanceTemplate;
import com.toir.enums.ApprovalResultAction;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MeterType;
import com.toir.enums.MaintenanceRegulationConditionOperator;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeEquipmentCountProjection;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.MaintenanceRegulationSparePartRequirementRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import com.toir.security.PermissionConstants;
import com.toir.security.SecurityAccessService;
import com.toir.util.AuditBuilderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
    EquipmentMaintenanceRuleRepository equipmentMaintenanceRuleRepository;

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

    @Mock
    MaintenanceRegulationSparePartRequirementRepository regulationSparePartRequirementRepository;

    @Mock
    SparePartRepository sparePartRepository;

    @Mock
    SecurityAccessService securityAccessService;

    @Mock
    MaintenanceMeterBaselineService meterBaselineService;

    @InjectMocks
    MaintenanceRegulationService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
    void searchIncludesStandaloneEquipmentRulesAsEquipmentScopedRegulations() {
        UUID equipmentId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, equipmentTypeId);
        equipment.setName("Pump A");
        EquipmentType equipmentType = new EquipmentType();
        equipmentType.setId(equipmentTypeId);
        equipmentType.setName("Pump");
        EquipmentMaintenanceRule rule = rule(ruleId, equipmentId, null, null, true);
        rule.setName("Pump A individual PM");
        rule.setAutomationAction(AutomationAction.CREATE_WORK_ORDER);
        rule.setDuplicatePolicy(DuplicatePolicy.ONE_ITEM_PER_CYCLE);
        rule.setDefaultPriority(PriorityLevel.HIGH);

        when(repository.searchPaginated(eq(null), eq(true), eq("PREVENTIVE"), eq("pump"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(equipmentRepository.findAllForMaintenanceRegulations(null)).thenReturn(List.of(equipment));
        when(equipmentMaintenanceRuleRepository.findAllByEquipmentIdInAndOptionalActive(Set.of(equipmentId), true))
                .thenReturn(List.of(rule));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(equipmentTypeId)))
                .thenReturn(List.of(equipmentType));

        var page = service.search(0, 20, "pump", null, true, "PREVENTIVE");

        assertThat(page.getContent()).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(ruleId);
            assertThat(dto.code()).isEqualTo("EMR-2026-0001");
            assertThat(dto.name()).isEqualTo("Pump A individual PM");
            assertThat(dto.equipmentTypeId()).isEqualTo(equipmentTypeId);
            assertThat(dto.equipmentTypeName()).isEqualTo("Pump");
            assertThat(dto.automationAction()).isEqualTo(AutomationAction.CREATE_WORK_ORDER);
            assertThat(dto.defaultPriority()).isEqualTo(PriorityLevel.HIGH);
            assertThat(dto.scope()).isEqualTo("EQUIPMENT");
            assertThat(dto.equipmentId()).isEqualTo(equipmentId);
            assertThat(dto.equipmentName()).isEqualTo("Pump A");
        });
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
    void equipmentWithRegulationsGroupsRulesByEquipment() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentType type = new EquipmentType();
        type.setId(typeId);
        type.setName("Pump");
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, "MR-2026-0001", true);
        regulation.setTemplateId(templateId);
        EquipmentMaintenanceRule rule = rule(UUID.randomUUID(), equipmentId, regulation.getId(), templateId, true);
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
        when(equipmentMaintenanceRuleRepository.findAllByEquipmentIdInAndOptionalActive(Set.of(equipmentId), true))
                .thenReturn(List.of(rule));
        when(repository.findAllByIdInAndIsDeletedFalse(Set.of(regulation.getId()))).thenReturn(List.of(regulation));
        when(operationRepository.findAllByTemplateIdInAndIsDeletedFalse(Set.of(templateId))).thenReturn(List.of(operation));

        var page = service.equipmentWithRegulations(typeId, true, null, null);

        assertThat(page.getContent()).hasSize(1);
        EquipmentWithRegulationsDto dto = page.getContent().getFirst();
        assertThat(dto.equipmentId()).isEqualTo(equipmentId);
        assertThat(dto.equipmentTypeName()).isEqualTo("Pump");
        assertThat(dto.regulations()).hasSize(1);
        assertThat(dto.regulations().getFirst().id()).isEqualTo(regulation.getId());
        assertThat(dto.regulations().getFirst().requiredSkill()).isEqualTo("Mechanic");
        assertThat(dto.regulations().getFirst().sparePartsRequired()).isEqualTo("Seal kit");
    }

    @Test
    void equipmentWithRegulationsReturnsEquipmentWithoutTypeAndEmptyRegulations() {
        UUID equipmentId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, null);
        when(equipmentRepository.findAllForMaintenanceRegulations(null)).thenReturn(List.of(equipment));
        when(equipmentMaintenanceRuleRepository.findAllByEquipmentIdInAndOptionalActive(Set.of(equipmentId), null))
                .thenReturn(List.of());

        var page = service.equipmentWithRegulations(null, null, null, null);

        assertThat(page.getContent()).hasSize(1);
        EquipmentWithRegulationsDto dto = page.getContent().getFirst();
        assertThat(dto.equipmentId()).isEqualTo(equipmentId);
        assertThat(dto.equipmentTypeId()).isNull();
        assertThat(dto.equipmentTypeName()).isNull();
        assertThat(dto.regulations()).isEmpty();
        verify(equipmentTypeRepository, never()).findAllByIdInAndIsDeletedFalse(any());
        verify(equipmentMaintenanceRuleRepository).findAllByEquipmentIdInAndOptionalActive(Set.of(equipmentId), null);
    }

    @Test
    void equipmentWithRegulationsHandlesTypedEquipmentWithoutRegulations() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentType type = new EquipmentType();
        type.setId(typeId);
        type.setName("Pump");

        when(equipmentRepository.findAllForMaintenanceRegulations(null)).thenReturn(List.of(equipment));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(typeId))).thenReturn(List.of(type));
        when(equipmentMaintenanceRuleRepository.findAllByEquipmentIdInAndOptionalActive(Set.of(equipmentId), null))
                .thenReturn(List.of());

        var page = service.equipmentWithRegulations(null, null, null, null);

        assertThat(page.getContent()).isEmpty();
        verify(equipmentMaintenanceRuleRepository).findAllByEquipmentIdInAndOptionalActive(Set.of(equipmentId), null);
    }

    @Test
    void equipmentWithRegulationsHandlesRegulationWithoutTemplate() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentType type = new EquipmentType();
        type.setId(typeId);
        type.setName("Pump");
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, "MR-2026-0001", true);
        regulation.setTemplateId(null);
        EquipmentMaintenanceRule rule = rule(UUID.randomUUID(), equipmentId, regulation.getId(), null, true);

        when(equipmentRepository.findAllForMaintenanceRegulations(null)).thenReturn(List.of(equipment));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(typeId))).thenReturn(List.of(type));
        when(equipmentMaintenanceRuleRepository.findAllByEquipmentIdInAndOptionalActive(Set.of(equipmentId), null))
                .thenReturn(List.of(rule));
        when(repository.findAllByIdInAndIsDeletedFalse(Set.of(regulation.getId()))).thenReturn(List.of(regulation));

        var page = service.equipmentWithRegulations(null, null, null, null);

        assertThat(page.getContent()).hasSize(1);
        EquipmentWithRegulationsDto dto = page.getContent().getFirst();
        assertThat(dto.equipmentId()).isEqualTo(equipmentId);
        assertThat(dto.equipmentTypeId()).isEqualTo(typeId);
        assertThat(dto.equipmentTypeName()).isEqualTo("Pump");
        assertThat(dto.regulations()).hasSize(1);
        assertThat(dto.regulations().getFirst().id()).isEqualTo(regulation.getId());
        assertThat(dto.regulations().getFirst().requiredSkill()).isNull();
        verify(operationRepository, never()).findAllByTemplateIdInAndIsDeletedFalse(any());
    }

    @Test
    void equipmentWithRegulationsIncludesStandaloneEquipmentRules() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        EquipmentType type = new EquipmentType();
        type.setId(typeId);
        type.setName("Pump");
        EquipmentMaintenanceRule rule = rule(ruleId, equipmentId, null, null, true);
        rule.setName("Pump A individual PM");
        rule.setNormativeLaborHours(2.4);

        when(equipmentRepository.findAllForMaintenanceRegulations(null)).thenReturn(List.of(equipment));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(typeId))).thenReturn(List.of(type));
        when(equipmentMaintenanceRuleRepository.findAllByEquipmentIdInAndOptionalActive(Set.of(equipmentId), null))
                .thenReturn(List.of(rule));

        var page = service.equipmentWithRegulations(null, null, null, null);

        assertThat(page.getContent()).hasSize(1);
        EquipmentWithRegulationsDto dto = page.getContent().getFirst();
        assertThat(dto.equipmentId()).isEqualTo(equipmentId);
        assertThat(dto.equipmentTypeName()).isEqualTo("Pump");
        assertThat(dto.regulations()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(ruleId);
            assertThat(summary.code()).isEqualTo("EMR-2026-0001");
            assertThat(summary.name()).isEqualTo("Pump A individual PM");
            assertThat(summary.category()).isEqualTo("PREVENTIVE");
            assertThat(summary.defaultDurationHours()).isEqualTo(3);
            assertThat(summary.active()).isTrue();
        });
        verify(repository, never()).findAllByIdInAndIsDeletedFalse(any());
        verify(operationRepository, never()).findAllByTemplateIdInAndIsDeletedFalse(any());
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
    void equipmentTypeWithRegulationsGroupsRegulationsByEquipmentType() {
        UUID typeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        EquipmentType type = new EquipmentType();
        type.setId(typeId);
        type.setCode("ET-2026-0001");
        type.setName("Pump");
        type.setCategory("PUMP");
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId, "MR-2026-0001", true);
        regulation.setTemplateId(templateId);
        MaintenanceTemplate template = template(templateId, typeId, MaintenanceKind.PREVENTIVE, true);
        MaintenanceOperation operation = new MaintenanceOperation();
        operation.setTemplate(template);
        operation.setRequiredSkill("Mechanic");
        operation.setToolsRequired("Wrench");
        EquipmentTypeEquipmentCountProjection count = new EquipmentTypeEquipmentCountProjection() {
            @Override
            public UUID getEquipmentTypeId() {
                return typeId;
            }

            @Override
            public Long getEquipmentCount() {
                return 4L;
            }
        };

        when(equipmentTypeRepository.existsByIdAndIsDeletedFalse(typeId)).thenReturn(true);
        when(repository.findAllByOptionalEquipmentTypeIdAndOptionalActive(typeId, true))
                .thenReturn(List.of(regulation));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(typeId))).thenReturn(List.of(type));
        when(equipmentRepository.countByEquipmentTypeIds(Set.of(typeId))).thenReturn(List.of(count));
        when(operationRepository.findAllByTemplateIdInAndIsDeletedFalse(Set.of(templateId))).thenReturn(List.of(operation));

        var page = service.equipmentTypeWithRegulations(typeId, true, 0, 10);

        assertThat(page.getContent()).hasSize(1);
        EquipmentTypeWithRegulationsDto dto = page.getContent().getFirst();
        assertThat(dto.equipmentTypeId()).isEqualTo(typeId);
        assertThat(dto.equipmentTypeCode()).isEqualTo("ET-2026-0001");
        assertThat(dto.equipmentTypeName()).isEqualTo("Pump");
        assertThat(dto.equipmentTypeCategory()).isEqualTo("PUMP");
        assertThat(dto.equipmentCount()).isEqualTo(4);
        assertThat(dto.regulations()).hasSize(1);
        assertThat(dto.regulations().getFirst().id()).isEqualTo(regulation.getId());
        assertThat(dto.regulations().getFirst().requiredSkill()).isEqualTo("Mechanic");
        assertThat(dto.regulations().getFirst().toolsRequired()).isEqualTo("Wrench");
    }

    @Test
    void equipmentTypeWithRegulationsRejectsPartialPagination() {
        assertThatThrownBy(() -> service.equipmentTypeWithRegulations(null, null, 0, null))
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
    void createActiveMeterTriggeredRegulationSeedsInitialMeterBaseline() {
        int year = Year.now().getValue();
        String codePrefix = "MR-" + year + "-";
        String expectedCode = "MR-" + year + "-0001";
        UUID regulationId = UUID.randomUUID();

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(MaintenanceRegulation.class))).thenAnswer(invocation -> {
            MaintenanceRegulation regulation = invocation.getArgument(0);
            regulation.setId(regulationId);
            return regulation;
        });

        service.create(request(null));

        verify(meterBaselineService).seedForRegulation(argThat(regulation ->
                regulationId.equals(regulation.getId())
                        && regulation.isActive()
                        && regulation.getTriggerMeterType() == MeterType.CUSTOM
                        && regulation.getTriggerMeterInterval().equals(10.0)
        ));
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
    void createWithAutomationConfigurationRequiresConfigurePermission() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "maintainer",
                "n/a",
                List.of(new SimpleGrantedAuthority(PermissionConstants.MAINTENANCE_REGULATION_CREATE))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(securityAccessService.hasPermission(authentication, PermissionConstants.MAINTENANCE_AUTOMATION_CONFIGURE))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(requestWithAutomationConfiguration()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionConstants.MAINTENANCE_AUTOMATION_CONFIGURE);

        verify(repository, never()).maxSequenceByCodePrefix(anyString());
        verify(repository, never()).save(any(MaintenanceRegulation.class));
    }

    @Test
    void createWithDefaultEquivalentAutomationFieldsDoesNotRequireConfigurePermission() {
        int year = Year.now().getValue();
        String codePrefix = "MR-" + year + "-";
        String expectedCode = "MR-" + year + "-0001";
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "maintainer",
                "n/a",
                List.of(new SimpleGrantedAuthority(PermissionConstants.MAINTENANCE_REGULATION_CREATE))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(MaintenanceRegulation.class))).thenAnswer(invocation -> {
            MaintenanceRegulation regulation = invocation.getArgument(0);
            regulation.setId(UUID.randomUUID());
            return regulation;
        });

        MaintenanceRegulationDto created = service.create(requestWithDefaultAutomationFields());

        assertThat(created.code()).isEqualTo(expectedCode);
        verify(securityAccessService, never())
                .hasPermission(any(), eq(PermissionConstants.MAINTENANCE_AUTOMATION_CONFIGURE));
        verify(repository).save(any(MaintenanceRegulation.class));
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
    void updateAutomationConfigurationRequiresConfigurePermissionWhenChanged() {
        UUID regulationId = UUID.randomUUID();
        UUID equipmentTypeId = UUID.randomUUID();
        MaintenanceRegulation existing = regulation(regulationId, equipmentTypeId, "MR-2026-0001", true);
        existing.setAutomationAction(AutomationAction.REQUIRE_APPROVAL);
        existing.setApprovalResultAction(ApprovalResultAction.CREATE_TASK);
        existing.setDuplicatePolicy(DuplicatePolicy.ONE_ITEM_PER_CYCLE);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "maintainer",
                "n/a",
                List.of(new SimpleGrantedAuthority(PermissionConstants.MAINTENANCE_REGULATION_UPDATE))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(repository.findByIdAndIsDeletedFalse(regulationId)).thenReturn(Optional.of(existing));
        when(securityAccessService.hasPermission(authentication, PermissionConstants.MAINTENANCE_AUTOMATION_CONFIGURE))
                .thenReturn(false);

        assertThatThrownBy(() -> service.update(regulationId, requestWithAutomationConfiguration()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionConstants.MAINTENANCE_AUTOMATION_CONFIGURE);

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
    void createWithoutTemplatePersistsRegulationSparePartRequirements() {
        int year = Year.now().getValue();
        String codePrefix = "MR-" + year + "-";
        String expectedCode = "MR-" + year + "-0001";
        UUID regulationId = UUID.randomUUID();
        UUID sparePartId = UUID.randomUUID();
        SparePart sparePart = sparePart(sparePartId, "BRG-001", "Bearing", "pcs");

        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(MaintenanceRegulation.class))).thenAnswer(invocation -> {
            MaintenanceRegulation regulation = invocation.getArgument(0);
            regulation.setId(regulationId);
            return regulation;
        });
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId)).thenReturn(Optional.of(sparePart));
        when(regulationSparePartRequirementRepository.findActiveByRegulationId(regulationId))
                .thenReturn(List.of())
                .thenReturn(List.of(regulationSpareRequirement(regulationId, sparePart, 2.0)));

        MaintenanceRegulationDto created = service.create(requestWithSpareParts(sparePartId, null));

        assertThat(created.templateId()).isNull();
        assertThat(created.sparePartRequirements()).hasSize(1);
        assertThat(created.sparePartRequirements().getFirst().sparePartCode()).isEqualTo("BRG-001");
        assertThat(created.sparePartRequirements().getFirst().sparePartName()).isEqualTo("Bearing");
        assertThat(created.sparePartRequirements().getFirst().quantity()).isEqualByComparingTo("2.0");
        assertThat(created.sparePartRequirements().getFirst().unit()).isEqualTo("pcs");

        ArgumentCaptor<Iterable<MaintenanceRegulationSparePartRequirement>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(regulationSparePartRequirementRepository).saveAll(captor.capture());
        MaintenanceRegulationSparePartRequirement saved =
                ((List<MaintenanceRegulationSparePartRequirement>) captor.getValue()).getFirst();
        assertThat(saved.getRegulationId()).isEqualTo(regulationId);
        assertThat(saved.getSparePart()).isEqualTo(sparePart);
        assertThat(saved.getQuantity()).isEqualByComparingTo("2.0");
        assertThat(saved.getUnit()).isEqualTo("pcs");
    }

    @Test
    void updateWithNullSparePartRequirementsKeepsExistingRequirements() {
        UUID regulationId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        MaintenanceRegulation existing = regulation(regulationId, typeId, "MR-2026-0001", true);

        when(repository.findByIdAndIsDeletedFalse(regulationId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(regulationSparePartRequirementRepository.findActiveByRegulationId(regulationId))
                .thenReturn(List.of());

        service.update(regulationId, requestWithNullableSpareParts(typeId, null));

        verify(regulationSparePartRequirementRepository, never()).saveAll(any());
    }

    @Test
    void updateWithEmptySparePartRequirementsSoftDeletesExistingRequirements() {
        UUID regulationId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        SparePart sparePart = sparePart(UUID.randomUUID(), "BRG-001", "Bearing", "pcs");
        MaintenanceRegulation existing = regulation(regulationId, typeId, "MR-2026-0001", true);
        MaintenanceRegulationSparePartRequirement existingRequirement =
                regulationSpareRequirement(regulationId, sparePart, 1.0);

        when(repository.findByIdAndIsDeletedFalse(regulationId)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(regulationSparePartRequirementRepository.findActiveByRegulationId(regulationId))
                .thenReturn(List.of(existingRequirement));

        service.update(regulationId, requestWithNullableSpareParts(typeId, List.of()));

        assertThat(existingRequirement.isDeleted()).isTrue();
        verify(regulationSparePartRequirementRepository).saveAll(List.of(existingRequirement));
    }

    @Test
    void createWithRegulationSparePartMismatchedUnitReturns400() {
        UUID sparePartId = UUID.randomUUID();
        when(sparePartRepository.findByIdAndIsDeletedFalse(sparePartId))
                .thenReturn(Optional.of(sparePart(sparePartId, "BRG-001", "Bearing", "pcs")));

        assertThatThrownBy(() -> service.create(requestWithSpareParts(sparePartId, "kg")))
                .isInstanceOfSatisfying(RestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("unit must match spare part unit");
                });

        verify(regulationSparePartRequirementRepository, never()).saveAll(any());
    }

    @Test
    void createWorkOrderAutomationWithoutTemplateDoesNotReturn400() {
        int year = Year.now().getValue();
        String codePrefix = "MR-" + year + "-";
        String expectedCode = "MR-" + year + "-0001";

        when(securityAccessService.hasPermission(any(), eq(PermissionConstants.MAINTENANCE_AUTOMATION_CONFIGURE)))
                .thenReturn(true);
        when(repository.maxSequenceByCodePrefix(codePrefix)).thenReturn(0L);
        when(repository.existsByCode(expectedCode)).thenReturn(false);
        when(repository.save(any(MaintenanceRegulation.class))).thenAnswer(invocation -> {
            MaintenanceRegulation regulation = invocation.getArgument(0);
            regulation.setId(UUID.randomUUID());
            return regulation;
        });

        MaintenanceRegulationDto created = service.create(requestWithAutomationConfiguration());

        assertThat(created.templateId()).isNull();
        assertThat(created.automationAction()).isEqualTo(AutomationAction.CREATE_WORK_ORDER);
        verify(repository).save(any(MaintenanceRegulation.class));
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

    private MaintenanceRegulationRequest requestWithSpareParts(UUID sparePartId, String unit) {
        return requestWithNullableSpareParts(
                UUID.randomUUID(),
                List.of(new MaintenanceRegulationSparePartRequirementRequest(
                        sparePartId,
                        new java.math.BigDecimal("2.0"),
                        unit,
                        "CRITICAL",
                        "keep ready",
                        true
                ))
        );
    }

    private MaintenanceRegulationRequest requestWithNullableSpareParts(
            UUID typeId,
            List<MaintenanceRegulationSparePartRequirementRequest> sparePartRequirements
    ) {
        return new MaintenanceRegulationRequest(
                null,
                "Monthly pump regulation",
                "Regulation description",
                typeId,
                null,
                MaintenanceKind.PREVENTIVE,
                3.0,
                true,
                PeriodicityUnit.MONTH,
                1,
                3,
                false,
                MeterType.CUSTOM,
                10.0,
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
                List.of(),
                sparePartRequirements
        );
    }

    private MaintenanceRegulationRequest requestWithAutomationConfiguration() {
        return new MaintenanceRegulationRequest(
                null,
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
                10.0,
                null,
                null,
                null,
                AutomationAction.CREATE_WORK_ORDER,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private MaintenanceRegulationRequest requestWithDefaultAutomationFields() {
        return new MaintenanceRegulationRequest(
                null,
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
                10.0,
                null,
                null,
                null,
                AutomationAction.REQUIRE_APPROVAL,
                ApprovalResultAction.CREATE_TASK,
                DuplicatePolicy.ONE_ITEM_PER_CYCLE,
                null,
                null,
                null,
                null,
                PriorityLevel.MEDIUM,
                true,
                null,
                null,
                List.of()
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

    private SparePart sparePart(UUID id, String code, String name, String unit) {
        SparePart sparePart = new SparePart();
        sparePart.setId(id);
        sparePart.setCode(code);
        sparePart.setName(name);
        sparePart.setUnit(unit);
        return sparePart;
    }

    private MaintenanceRegulationSparePartRequirement regulationSpareRequirement(
            UUID regulationId,
            SparePart sparePart,
            double quantity
    ) {
        MaintenanceRegulationSparePartRequirement requirement =
                new MaintenanceRegulationSparePartRequirement();
        requirement.setId(UUID.randomUUID());
        requirement.setRegulationId(regulationId);
        requirement.setSparePart(sparePart);
        requirement.setSparePartId(sparePart.getId());
        requirement.setQuantity(new java.math.BigDecimal(Double.toString(quantity)));
        requirement.setUnit(sparePart.getUnit());
        requirement.setCriticality("CRITICAL");
        requirement.setNotes("keep ready");
        requirement.setActive(true);
        return requirement;
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

    private EquipmentMaintenanceRule rule(UUID id,
                                          UUID equipmentId,
                                          UUID baseRegulationId,
                                          UUID templateId,
                                          boolean active) {
        EquipmentMaintenanceRule rule = new EquipmentMaintenanceRule();
        rule.setId(id);
        rule.setEquipmentId(equipmentId);
        rule.setBaseRegulationId(baseRegulationId);
        rule.setTemplateId(templateId);
        rule.setCode("EMR-2026-0001");
        rule.setName("Monthly pump regulation override");
        rule.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        rule.setNormativeLaborHours(3.0);
        rule.setActive(active);
        rule.setPeriodicityUnit(PeriodicityUnit.MONTH);
        rule.setPeriodicityValue(1);
        rule.setRequiresShutdown(false);
        return rule;
    }
}

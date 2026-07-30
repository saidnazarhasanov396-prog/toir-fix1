package com.toir.service.maintanance;

import com.toir.repository.equipment.EquipmentTypeRepository;

import com.toir.dto.maintenanceregulation.EquipmentWithRegulationsDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationAttributeConditionRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationFilter;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationSparePartRequirementRequest;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationStatsDto;
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
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceOperationRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import com.toir.repository.maintenance.MaintenanceRegulationReadRepository;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Year;
import java.util.List;
import java.util.Map;
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
    MaintenanceRegulationReadRepository readRepository;

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
    void searchUsesCanonicalDatabasePageAndPreservesItsOrderAndMetadata() {
        UUID equipmentTypeId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        MaintenanceRegulation first = regulation(firstId, equipmentTypeId, "MR-2026-0001", true);
        MaintenanceRegulation second = regulation(secondId, equipmentTypeId, "MR-2026-0002", false);
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(
                "pump", MaintenanceKind.PREVENTIVE, equipmentTypeId, null);
        PageRequest pageable = PageRequest.of(2, 20);
        when(equipmentTypeRepository.existsByIdAndIsDeletedFalse(equipmentTypeId)).thenReturn(true);
        when(readRepository.findGeneral(filter, pageable)).thenReturn(new PageImpl<>(
                List.of(
                        new MaintenanceRegulationReadRepository.GeneralKey(
                                MaintenanceRegulationReadRepository.DisplaySource.REGULATION, secondId),
                        new MaintenanceRegulationReadRepository.GeneralKey(
                                MaintenanceRegulationReadRepository.DisplaySource.REGULATION, firstId)
                ),
                pageable,
                43
        ));
        when(repository.findAllByIdInAndIsDeletedFalse(Set.of(firstId, secondId)))
                .thenReturn(List.of(first, second));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(equipmentTypeId)))
                .thenReturn(List.of(equipmentType(equipmentTypeId, "Pump")));

        var page = service.search(2, 20, filter);

        assertThat(page.getContent()).extracting(MaintenanceRegulationDto::id)
                .containsExactly(secondId, firstId);
        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getTotalElements()).isEqualTo(43);
        verify(readRepository).findGeneral(filter, pageable);
    }

    @Test
    void searchMapsStandaloneRuleFromBoundedGeneralPage() {
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
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(
                "pump", MaintenanceKind.PREVENTIVE, null, true);
        when(readRepository.findGeneral(filter, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(
                List.of(new MaintenanceRegulationReadRepository.GeneralKey(
                        MaintenanceRegulationReadRepository.DisplaySource.EQUIPMENT_RULE, ruleId)),
                PageRequest.of(0, 20),
                1
        ));
        when(equipmentMaintenanceRuleRepository.findAllByIdInAndIsDeletedFalse(Set.of(ruleId)))
                .thenReturn(List.of(rule));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(Set.of(equipmentId)))
                .thenReturn(List.of(equipment));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(equipmentTypeId)))
                .thenReturn(List.of(equipmentType));

        var page = service.search(0, 20, filter);

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
        MaintenanceRegulationFilter filter =
                new MaintenanceRegulationFilter(null, null, equipmentTypeId, null);

        assertThatThrownBy(() -> service.search(0, 20, filter))
                .isInstanceOfSatisfying(RestException.class, ex ->
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(readRepository);
    }

    @Test
    void equipmentWithRegulationsUsesPagedIdsAndEffectiveLinkedStatus() {
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
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(
                null, MaintenanceKind.PREVENTIVE, typeId, false);
        PageRequest pageable = PageRequest.of(0, 10);
        when(equipmentTypeRepository.existsByIdAndIsDeletedFalse(typeId)).thenReturn(true);
        when(readRepository.findEquipmentIds(filter, pageable)).thenReturn(
                new PageImpl<>(List.of(equipmentId), pageable, 1));
        when(readRepository.findEquipmentMatches(filter, List.of(equipmentId))).thenReturn(List.of(
                new MaintenanceRegulationReadRepository.EquipmentMatch(
                        equipmentId,
                        MaintenanceRegulationReadRepository.DisplaySource.REGULATION,
                        regulation.getId(),
                        false
                )
        ));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(Set.of(equipmentId)))
                .thenReturn(List.of(equipment));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(typeId))).thenReturn(List.of(type));
        when(repository.findAllByIdInAndIsDeletedFalse(Set.of(regulation.getId()))).thenReturn(List.of(regulation));
        when(operationRepository.findAllByTemplateIdInAndIsDeletedFalse(Set.of(templateId))).thenReturn(List.of(operation));

        var page = service.equipmentWithRegulations(0, 10, filter);

        assertThat(page.getContent()).hasSize(1);
        EquipmentWithRegulationsDto dto = page.getContent().getFirst();
        assertThat(dto.equipmentId()).isEqualTo(equipmentId);
        assertThat(dto.equipmentTypeName()).isEqualTo("Pump");
        assertThat(dto.regulations()).hasSize(1);
        assertThat(dto.regulations().getFirst().id()).isEqualTo(regulation.getId());
        assertThat(dto.regulations().getFirst().active()).isFalse();
        assertThat(dto.regulations().getFirst().requiredSkill()).isEqualTo("Mechanic");
        assertThat(dto.regulations().getFirst().sparePartsRequired()).isEqualTo("Seal kit");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void equipmentWithRegulationsDropsParentIfBulkMatchIsUnexpectedlyEmpty() {
        UUID equipmentId = UUID.randomUUID();
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(null, null, null, null);
        PageRequest pageable = PageRequest.of(0, 10);
        when(readRepository.findEquipmentIds(filter, pageable))
                .thenReturn(new PageImpl<>(List.of(equipmentId), pageable, 1));
        when(readRepository.findEquipmentMatches(filter, List.of(equipmentId))).thenReturn(List.of());

        var page = service.equipmentWithRegulations(0, 10, filter);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(equipmentRepository, never()).findAllByIdInAndIsDeletedFalse(any());
    }

    @Test
    void equipmentWithRegulationsDeduplicatesDisplayIdentityInDatabaseOrder() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        UUID ruleId = UUID.randomUUID();
        EquipmentMaintenanceRule rule = rule(ruleId, equipmentId, null, null, true);
        rule.setName("Pump A individual PM");
        rule.setNormativeLaborHours(2.4);
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(null, null, null, null);
        PageRequest pageable = PageRequest.of(0, 10);
        when(readRepository.findEquipmentIds(filter, pageable))
                .thenReturn(new PageImpl<>(List.of(equipmentId), pageable, 1));
        when(readRepository.findEquipmentMatches(filter, List.of(equipmentId))).thenReturn(List.of(
                new MaintenanceRegulationReadRepository.EquipmentMatch(
                        equipmentId,
                        MaintenanceRegulationReadRepository.DisplaySource.EQUIPMENT_RULE,
                        ruleId,
                        true
                ),
                new MaintenanceRegulationReadRepository.EquipmentMatch(
                        equipmentId,
                        MaintenanceRegulationReadRepository.DisplaySource.EQUIPMENT_RULE,
                        ruleId,
                        true
                )
        ));
        when(equipmentRepository.findAllByIdInAndIsDeletedFalse(Set.of(equipmentId)))
                .thenReturn(List.of(equipment));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(typeId)))
                .thenReturn(List.of(equipmentType(typeId, "Pump")));
        when(equipmentMaintenanceRuleRepository.findAllByIdInAndIsDeletedFalse(Set.of(ruleId)))
                .thenReturn(List.of(rule));

        var page = service.equipmentWithRegulations(0, 10, filter);

        assertThat(page.getContent()).singleElement().satisfies(dto ->
                assertThat(dto.regulations()).singleElement().satisfies(summary -> {
                    assertThat(summary.id()).isEqualTo(ruleId);
                    assertThat(summary.defaultDurationHours()).isEqualTo(3);
                }));
    }

    @Test
    void equipmentTypeWithRegulationsReturnsFilteredNestedCountAndEquipmentCount() {
        UUID typeId = UUID.randomUUID();
        UUID regulationId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        EquipmentType type = equipmentType(typeId, "Pump");
        type.setCode("ET-2026-0001");
        type.setCategory("PUMP");
        MaintenanceRegulation regulation = regulation(regulationId, typeId, "MR-2026-0001", true);
        EquipmentMaintenanceRule rule = rule(ruleId, UUID.randomUUID(), null, null, false);
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(
                "pump", null, typeId, null);
        PageRequest pageable = PageRequest.of(0, 10);
        when(equipmentTypeRepository.existsByIdAndIsDeletedFalse(typeId)).thenReturn(true);
        when(readRepository.findEquipmentTypeIds(filter, pageable))
                .thenReturn(new PageImpl<>(List.of(typeId), pageable, 1));
        when(readRepository.findEquipmentTypeMatches(filter, List.of(typeId))).thenReturn(List.of(
                new MaintenanceRegulationReadRepository.EquipmentTypeMatch(
                        typeId,
                        MaintenanceRegulationReadRepository.DisplaySource.REGULATION,
                        regulationId,
                        true
                ),
                new MaintenanceRegulationReadRepository.EquipmentTypeMatch(
                        typeId,
                        MaintenanceRegulationReadRepository.DisplaySource.EQUIPMENT_RULE,
                        ruleId,
                        false
                )
        ));
        when(readRepository.countMatchingEquipmentByType(filter, List.of(typeId)))
                .thenReturn(Map.of(typeId, 4));
        when(equipmentTypeRepository.findAllByIdInAndIsDeletedFalse(Set.of(typeId))).thenReturn(List.of(type));
        when(repository.findAllByIdInAndIsDeletedFalse(Set.of(regulationId))).thenReturn(List.of(regulation));
        when(equipmentMaintenanceRuleRepository.findAllByIdInAndIsDeletedFalse(Set.of(ruleId)))
                .thenReturn(List.of(rule));

        var page = service.equipmentTypeWithRegulations(0, 10, filter);

        assertThat(page.getContent()).singleElement().satisfies(dto -> {
            assertThat(dto.equipmentTypeId()).isEqualTo(typeId);
            assertThat(dto.equipmentCount()).isEqualTo(4);
            assertThat(dto.regulations()).extracting(summary -> summary.id())
                    .containsExactly(regulationId, ruleId);
        });
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void statsValidatesEquipmentTypeAndReturnsDatabaseAggregate() {
        UUID typeId = UUID.randomUUID();
        MaintenanceRegulationFilter filter =
                new MaintenanceRegulationFilter("pump", null, typeId, false);
        MaintenanceRegulationStatsDto expected = new MaintenanceRegulationStatsDto(7, 0, 3, 2);
        when(equipmentTypeRepository.existsByIdAndIsDeletedFalse(typeId)).thenReturn(true);
        when(readRepository.stats(filter)).thenReturn(expected);

        assertThat(service.stats(filter)).isEqualTo(expected);
        verify(readRepository).stats(filter);
    }

    @Test
    void readFailureDoesNotExposeSqlOrStorageDetails() {
        MaintenanceRegulationFilter filter = new MaintenanceRegulationFilter(null, null, null, null);
        when(readRepository.findGeneral(filter, PageRequest.of(0, 20)))
                .thenThrow(new DataAccessResourceFailureException(
                        "relation maintenance_regulations does not exist at SQL position 418"));

        assertThatThrownBy(() -> service.search(0, 20, filter))
                .isInstanceOfSatisfying(RestException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(exception.getMessage())
                            .isEqualTo("Unable to load maintenance regulations");
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

    private EquipmentType equipmentType(UUID id, String name) {
        EquipmentType type = new EquipmentType();
        type.setId(id);
        type.setCode("ET-2026-0001");
        type.setName(name);
        return type;
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

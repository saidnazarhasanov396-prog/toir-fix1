package com.toir.service.maintanance;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import com.toir.enums.AutomationAction;
import com.toir.enums.DuplicatePolicy;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.enums.EquipmentStatus;
import com.toir.enums.MaintenanceKind;
import com.toir.enums.MaintenanceRegulationConditionOperator;
import com.toir.enums.MaintenanceRuleOrigin;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.enums.MeterType;
import com.toir.enums.PeriodicityUnit;
import com.toir.enums.PriorityLevel;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentMaintenanceEffectiveRuleResolverTest {

    @Mock
    EquipmentRepository equipmentRepository;

    @Mock
    MaintenanceRegulationRepository regulationRepository;

    @Mock
    EquipmentMaintenanceRuleRepository ruleRepository;

    @Mock
    MaintenanceRegulationAttributeConditionRepository conditionRepository;

    @Mock
    EquipmentAttributeDefinitionRepository definitionRepository;

    @Mock
    EquipmentAttributeValueRepository valueRepository;

    EquipmentMaintenanceEffectiveRuleResolver resolver;

    @BeforeEach
    void setUp() {
        MaintenanceRegulationApplicabilityService applicabilityService = new MaintenanceRegulationApplicabilityService(
                conditionRepository,
                definitionRepository,
                valueRepository
        );
        resolver = new EquipmentMaintenanceEffectiveRuleResolver(
                equipmentRepository,
                regulationRepository,
                ruleRepository,
                conditionRepository,
                definitionRepository,
                valueRepository,
                applicabilityService
        );
    }

    @Test
    void excludesTypeRegulationWhenAttributeConditionDoesNotMatch() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId);
        MaintenanceRegulationAttributeCondition condition = condition(regulation.getId(), "capacity", 10.0);
        EquipmentAttributeDefinition definition = definition(typeId, "capacity");
        EquipmentAttributeValue value = value(equipmentId, definition.getId(), 8.0);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(ruleRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of());
        when(regulationRepository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(typeId)).thenReturn(List.of(regulation));
        when(conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(List.of(regulation.getId()))).thenReturn(List.of(condition));
        when(definitionRepository.findAllByEquipmentTypeIdInAndIsDeletedFalse(Set.of(typeId))).thenReturn(List.of(definition));
        when(valueRepository.findAllByEquipmentIdInAndIsDeletedFalse(Set.of(equipmentId))).thenReturn(List.of(value));

        List<EquipmentMaintenanceEffectiveRule> resolved = resolver.resolve(equipmentId);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().applicable()).isFalse();
        assertThat(resolved.getFirst().blockedReason()).contains("attribute conditions");
        assertThat(resolver.resolveApplicable(equipmentId)).isEmpty();
    }

    @Test
    void disabledEquipmentRuleSuppressesInheritedRegulation() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId);
        EquipmentMaintenanceRule disablingRule = overrideRule(equipmentId, regulation.getId(), UUID.randomUUID());
        disablingRule.setDisablesBaseRegulation(true);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(ruleRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(disablingRule));
        when(regulationRepository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(typeId)).thenReturn(List.of(regulation));
        when(conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(List.of(regulation.getId()))).thenReturn(List.of());

        List<EquipmentMaintenanceEffectiveRule> resolved = resolver.resolve(equipmentId);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().applicable()).isFalse();
        assertThat(resolved.getFirst().equipmentMaintenanceRuleId()).isEqualTo(disablingRule.getId());
        assertThat(resolved.getFirst().blockedReason()).contains("Disabled");
        assertThat(resolver.resolveApplicable(equipmentId)).isEmpty();
    }

    @Test
    void overrideRuleUsesRuleScheduleButBaseAutomationPolicy() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID baseTemplateId = UUID.randomUUID();
        UUID overrideTemplateId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation regulation = regulation(UUID.randomUUID(), typeId);
        regulation.setTemplateId(baseTemplateId);
        regulation.setAutomationAction(AutomationAction.CREATE_WORK_ORDER);
        regulation.setDuplicatePolicy(DuplicatePolicy.ONE_OPEN_ITEM_PER_RULE);
        regulation.setDefaultPriority(PriorityLevel.HIGH);
        regulation.setTriggerMeterInterval(500.0);
        EquipmentMaintenanceRule override = overrideRule(equipmentId, regulation.getId(), overrideTemplateId);
        override.setTriggerMeterInterval(250.0);

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(ruleRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(override));
        when(regulationRepository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(typeId)).thenReturn(List.of(regulation));
        when(conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(List.of(regulation.getId()))).thenReturn(List.of());

        List<EquipmentMaintenanceEffectiveRule> resolved = resolver.resolveApplicable(equipmentId);

        assertThat(resolved).hasSize(1);
        EquipmentMaintenanceEffectiveRule effective = resolved.getFirst();
        assertThat(effective.source()).isEqualTo(MaintenanceRuleOrigin.TYPE_REGULATION_WITH_OVERRIDE);
        assertThat(effective.regulationId()).isEqualTo(regulation.getId());
        assertThat(effective.equipmentMaintenanceRuleId()).isEqualTo(override.getId());
        assertThat(effective.templateId()).isEqualTo(overrideTemplateId);
        assertThat(effective.triggerMeterInterval()).isEqualTo(250.0);
        assertThat(effective.automationAction()).isEqualTo(AutomationAction.CREATE_WORK_ORDER);
        assertThat(effective.duplicatePolicy()).isEqualTo(DuplicatePolicy.ONE_OPEN_ITEM_PER_RULE);
        assertThat(effective.defaultPriority()).isEqualTo(PriorityLevel.HIGH);
    }

    @Test
    void equipmentSpecificProfileDoesNotInheritUnselectedTypeRegulations() {
        UUID equipmentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Equipment equipment = equipment(equipmentId, typeId);
        MaintenanceRegulation selectedRegulation = regulation(UUID.randomUUID(), typeId);
        selectedRegulation.setCode("MR-SELECTED");
        MaintenanceRegulation unselectedRegulation = regulation(UUID.randomUUID(), typeId);
        unselectedRegulation.setCode("MR-UNSELECTED");
        EquipmentMaintenanceRule selectedOverride = overrideRule(equipmentId, selectedRegulation.getId(), UUID.randomUUID());

        when(equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)).thenReturn(Optional.of(equipment));
        when(ruleRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId)).thenReturn(List.of(selectedOverride));
        when(regulationRepository.findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(typeId))
                .thenReturn(List.of(selectedRegulation, unselectedRegulation));
        when(conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(List.of(selectedRegulation.getId())))
                .thenReturn(List.of());
        when(conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(List.of(unselectedRegulation.getId())))
                .thenReturn(List.of());

        List<EquipmentMaintenanceEffectiveRule> resolved = resolver.resolveApplicable(equipmentId);

        assertThat(resolved)
                .extracting(EquipmentMaintenanceEffectiveRule::regulationId)
                .containsExactly(selectedRegulation.getId());
        assertThat(resolved.getFirst().equipmentMaintenanceRuleId()).isEqualTo(selectedOverride.getId());
    }

    private Equipment equipment(UUID id, UUID typeId) {
        Equipment equipment = new Equipment();
        ReflectionTestUtils.setField(equipment, "id", id);
        equipment.setEquipmentTypeId(typeId);
        equipment.setStatus(EquipmentStatus.ACTIVE);
        equipment.setCode("EQ-1");
        equipment.setName("Pump");
        return equipment;
    }

    private MaintenanceRegulation regulation(UUID id, UUID typeId) {
        MaintenanceRegulation regulation = new MaintenanceRegulation();
        ReflectionTestUtils.setField(regulation, "id", id);
        regulation.setCode("MR-1");
        regulation.setName("Base service");
        regulation.setEquipmentTypeId(typeId);
        regulation.setTemplateId(UUID.randomUUID());
        regulation.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        regulation.setNormativeLaborHours(2.0);
        regulation.setActive(true);
        regulation.setPeriodicityUnit(PeriodicityUnit.MONTH);
        regulation.setPeriodicityValue(1);
        regulation.setTriggerMeterType(MeterType.ENGINE_HOURS);
        regulation.setTriggerMeterInterval(500.0);
        regulation.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        regulation.setAutomationAction(AutomationAction.TRACK_ONLY);
        regulation.setDuplicatePolicy(DuplicatePolicy.ONE_ITEM_PER_CYCLE);
        regulation.setDefaultPriority(PriorityLevel.MEDIUM);
        regulation.setRequiresApproval(false);
        return regulation;
    }

    private EquipmentMaintenanceRule overrideRule(UUID equipmentId, UUID baseRegulationId, UUID templateId) {
        EquipmentMaintenanceRule rule = new EquipmentMaintenanceRule();
        ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        rule.setCode("EMR-1");
        rule.setName("Override service");
        rule.setEquipmentId(equipmentId);
        rule.setBaseRegulationId(baseRegulationId);
        rule.setTemplateId(templateId);
        rule.setMaintenanceKind(MaintenanceKind.PREVENTIVE);
        rule.setNormativeLaborHours(1.0);
        rule.setActive(true);
        rule.setPeriodicityUnit(PeriodicityUnit.MONTH);
        rule.setPeriodicityValue(1);
        rule.setTriggerMeterType(MeterType.ENGINE_HOURS);
        rule.setTriggerMeterInterval(250.0);
        rule.setTriggerPolicy(MaintenanceTriggerPolicy.ANY);
        return rule;
    }

    private MaintenanceRegulationAttributeCondition condition(UUID regulationId, String key, double value) {
        MaintenanceRegulationAttributeCondition condition = new MaintenanceRegulationAttributeCondition();
        condition.setRegulationId(regulationId);
        condition.setAttributeKey(key);
        condition.setOperator(MaintenanceRegulationConditionOperator.GREATER_THAN);
        condition.setValueNumber(value);
        return condition;
    }

    private EquipmentAttributeDefinition definition(UUID typeId, String key) {
        EquipmentAttributeDefinition definition = new EquipmentAttributeDefinition();
        ReflectionTestUtils.setField(definition, "id", UUID.randomUUID());
        definition.setEquipmentTypeId(typeId);
        definition.setKey(key);
        definition.setLabel("Capacity");
        definition.setDataType(EquipmentAttributeDataType.NUMBER);
        return definition;
    }

    private EquipmentAttributeValue value(UUID equipmentId, UUID definitionId, double value) {
        EquipmentAttributeValue attributeValue = new EquipmentAttributeValue();
        attributeValue.setEquipmentId(equipmentId);
        attributeValue.setAttributeDefinitionId(definitionId);
        attributeValue.setValueNumber(value);
        return attributeValue;
    }
}

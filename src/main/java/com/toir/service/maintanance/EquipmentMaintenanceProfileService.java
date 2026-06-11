package com.toir.service.maintanance;

import com.toir.dto.equipmentmaintenance.EquipmentMaintenanceProfileDto;
import com.toir.dto.equipmentmaintenance.EquipmentMaintenanceRuleDto;
import com.toir.dto.equipmentmaintenance.EquipmentMaintenanceRuleRequest;
import com.toir.dto.equipmentmaintenance.EffectiveMaintenanceRuleDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationDto;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import com.toir.enums.MaintenanceRecalculationPolicy;
import com.toir.enums.MaintenanceRegulationConditionOperator;
import com.toir.enums.MaintenanceRuleOrigin;
import com.toir.enums.MaintenanceTriggerPolicy;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import com.toir.repository.maintenance.MaintenanceTemplateRepository;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EquipmentMaintenanceProfileService {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 50;

    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final EquipmentMaintenanceRuleRepository ruleRepository;
    private final MaintenanceTemplateRepository templateRepository;
    private final MaintenanceDueCalculationService maintenanceDueCalculationService;
    private final MaintenanceRegulationAttributeConditionRepository conditionRepository;
    private final EquipmentAttributeDefinitionRepository attributeDefinitionRepository;
    private final EquipmentAttributeValueRepository attributeValueRepository;
    private final EquipmentMaintenanceEffectiveRuleResolver effectiveRuleResolver;

    @Transactional(readOnly = true)
    public EquipmentMaintenanceProfileDto getProfile(UUID equipmentId) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        List<MaintenanceRegulationDto> inheritedRules = regulationRepository
                .findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(equipment.getEquipmentTypeId())
                .stream()
                .map(MaintenanceRegulationDto::from)
                .toList();
        List<EquipmentMaintenanceRuleDto> individualRules = ruleRepository
                .findAllByEquipmentIdAndIsDeletedFalse(equipmentId)
                .stream()
                .map(EquipmentMaintenanceRuleDto::from)
                .toList();
        List<EffectiveMaintenanceRuleDto> effectiveRules = effectiveRuleResolver.resolveApplicable(equipmentId)
                .stream()
                .map(this::fromEffectiveRule)
                .toList();
        return new EquipmentMaintenanceProfileDto(
                equipment.getId(),
                equipment.getEquipmentTypeId(),
                inheritedRules,
                individualRules,
                effectiveRules
        );
    }

    private EffectiveMaintenanceRuleDto fromEffectiveRule(EquipmentMaintenanceEffectiveRule rule) {
        return new EffectiveMaintenanceRuleDto(
                rule.equipmentMaintenanceRuleId() == null ? rule.regulationId() : rule.equipmentMaintenanceRuleId(),
                rule.regulationId(),
                rule.equipmentMaintenanceRuleId(),
                rule.equipmentId(),
                rule.regulationId(),
                rule.templateId(),
                rule.code(),
                rule.name(),
                rule.description(),
                rule.maintenanceKind(),
                rule.normativeLaborHours(),
                rule.active(),
                rule.periodicityUnit(),
                rule.periodicityValue(),
                rule.toleranceDays(),
                rule.requiresShutdown(),
                rule.triggerMeterType(),
                rule.triggerMeterInterval(),
                rule.triggerPolicy(),
                rule.recalculationPolicy(),
                rule.source(),
                rule.overrideReason(),
                maintenanceDueCalculationService.calculate(rule)
        );
    }

    @Transactional
    public EquipmentMaintenanceRuleDto createRule(UUID equipmentId, EquipmentMaintenanceRuleRequest request) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        validateReferences(equipment, request);

        EquipmentMaintenanceRule rule = new EquipmentMaintenanceRule();
        rule.setEquipmentId(equipment.getId());
        rule.setCode(generateCode());
        apply(rule, request);
        return EquipmentMaintenanceRuleDto.from(ruleRepository.save(rule));
    }

    @Transactional
    public EquipmentMaintenanceRuleDto updateRule(UUID equipmentId, UUID ruleId, EquipmentMaintenanceRuleRequest request) {
        Equipment equipment = equipmentOrThrow(equipmentId);
        validateReferences(equipment, request);
        EquipmentMaintenanceRule rule = ruleRepository.findByIdAndIsDeletedFalse(ruleId)
                .orElseThrow(() -> RestException.notFound("Equipment maintenance rule not found: " + ruleId));
        if (!rule.getEquipmentId().equals(equipmentId)) {
            throw RestException.badRequest("Equipment maintenance rule belongs to another equipment");
        }
        apply(rule, request);
        return EquipmentMaintenanceRuleDto.from(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(UUID equipmentId, UUID ruleId) {
        equipmentOrThrow(equipmentId);
        EquipmentMaintenanceRule rule = ruleRepository.findByIdAndIsDeletedFalse(ruleId)
                .orElseThrow(() -> RestException.notFound("Equipment maintenance rule not found: " + ruleId));
        if (!rule.getEquipmentId().equals(equipmentId)) {
            throw RestException.badRequest("Equipment maintenance rule belongs to another equipment");
        }
        rule.setDeleted(true);
        ruleRepository.save(rule);
    }

    private Equipment equipmentOrThrow(UUID equipmentId) {
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
    }

    private void validateReferences(Equipment equipment, EquipmentMaintenanceRuleRequest request) {
        if (request.baseRegulationId() != null) {
            var base = regulationRepository.findByIdAndIsDeletedFalse(request.baseRegulationId())
                    .orElseThrow(() -> RestException.notFound("Base maintenance regulation not found: " + request.baseRegulationId()));
            if (!base.getEquipmentTypeId().equals(equipment.getEquipmentTypeId())) {
                throw RestException.badRequest("Base regulation belongs to another equipment type");
            }
        }
        if (request.templateId() != null && !templateRepository.existsByIdAndIsDeletedFalse(request.templateId())) {
            throw RestException.notFound("Maintenance template not found: " + request.templateId());
        }
    }

    private void apply(EquipmentMaintenanceRule rule, EquipmentMaintenanceRuleRequest request) {
        rule.setBaseRegulationId(request.baseRegulationId());
        rule.setTemplateId(request.templateId());
        rule.setName(request.name().trim());
        rule.setDescription(blankToNull(request.description()));
        rule.setMaintenanceKind(request.maintenanceKind());
        rule.setNormativeLaborHours(request.normativeLaborHours());
        rule.setActive(request.active() == null || request.active());
        rule.setPeriodicityUnit(request.periodicityUnit());
        rule.setPeriodicityValue(request.periodicityValue());
        rule.setToleranceDays(request.toleranceDays());
        rule.setRequiresShutdown(request.requiresShutdown());
        rule.setTriggerMeterType(request.triggerMeterType());
        rule.setTriggerMeterInterval(request.triggerMeterInterval());
        rule.setTriggerPolicy(request.triggerPolicy() == null ? MaintenanceTriggerPolicy.ANY : request.triggerPolicy());
        rule.setRecalculationPolicy(request.recalculationPolicy() == null
                ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                : request.recalculationPolicy());
        rule.setDisablesBaseRegulation(Boolean.TRUE.equals(request.disablesBaseRegulation()));
        rule.setOverrideReason(blankToNull(request.overrideReason()));
        rule.setInitialSchedulePolicy(request.initialSchedulePolicy());
        rule.setAutomationAction(request.automationAction());
        rule.setApprovalResultAction(request.approvalResultAction());
        rule.setDuplicatePolicy(request.duplicatePolicy());
        rule.setLeadTimeDays(request.leadTimeDays());
        rule.setLeadMeterPercent(request.leadMeterPercent());
        rule.setDefaultDepartmentId(request.defaultDepartmentId());
        rule.setDefaultResponsibleId(request.defaultResponsibleId());
        rule.setDefaultPriority(request.defaultPriority());
        rule.setRequiresApproval(request.requiresApproval());
        rule.setApprovalRole(blankToNull(request.approvalRole()));
        rule.setApprovalPermission(blankToNull(request.approvalPermission()));
    }

    private List<EffectiveMaintenanceRuleDto> effectiveRules(Equipment equipment, List<EquipmentMaintenanceRule> individualRules) {
        Map<UUID, EquipmentMaintenanceRule> overrideByBaseId = individualRules.stream()
                .filter(EquipmentMaintenanceRule::isActive)
                .filter(rule -> rule.getBaseRegulationId() != null)
                .collect(Collectors.toMap(
                        EquipmentMaintenanceRule::getBaseRegulationId,
                        Function.identity(),
                        (left, right) -> left
                ));
        List<EffectiveMaintenanceRuleDto> effective = new java.util.ArrayList<>();
        List<MaintenanceRegulation> inheritedRegulations = regulationRepository
                .findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(equipment.getEquipmentTypeId());
        Map<UUID, List<MaintenanceRegulationAttributeCondition>> conditionsByRegulationId =
                loadConditionsByRegulationId(inheritedRegulations);
        AttributeIndex attributeIndex = loadAttributeIndex(equipment);
        for (MaintenanceRegulation regulation : inheritedRegulations) {
            if (!matchesConditions(equipment,
                    conditionsByRegulationId.getOrDefault(regulation.getId(), List.of()),
                    attributeIndex)) {
                continue;
            }
            EquipmentMaintenanceRule override = overrideByBaseId.get(regulation.getId());
            if (override == null) {
                effective.add(fromRegulation(equipment.getId(), regulation));
            } else if (!override.isDisablesBaseRegulation()) {
                effective.add(fromRule(override, MaintenanceRuleOrigin.TYPE_REGULATION_WITH_OVERRIDE));
            }
        }
        individualRules.stream()
                .filter(EquipmentMaintenanceRule::isActive)
                .filter(rule -> rule.getBaseRegulationId() == null)
                .map(rule -> fromRule(rule, MaintenanceRuleOrigin.INDIVIDUAL_RULE))
                .forEach(effective::add);
        return effective;
    }

    private Map<UUID, List<MaintenanceRegulationAttributeCondition>> loadConditionsByRegulationId(
            List<MaintenanceRegulation> regulations) {
        if (regulations.isEmpty()) {
            return Map.of();
        }
        List<UUID> regulationIds = regulations.stream()
                .map(MaintenanceRegulation::getId)
                .toList();
        return conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(regulationIds)
                .stream()
                .collect(Collectors.groupingBy(MaintenanceRegulationAttributeCondition::getRegulationId));
    }

    private AttributeIndex loadAttributeIndex(Equipment equipment) {
        if (equipment.getEquipmentTypeId() == null || equipment.getId() == null) {
            return new AttributeIndex(Map.of(), Map.of());
        }
        Map<UUID, Map<String, EquipmentAttributeDefinition>> definitionsByTypeId =
                attributeDefinitionRepository.findAllByEquipmentTypeIdInAndIsDeletedFalse(Set.of(equipment.getEquipmentTypeId()))
                        .stream()
                        .collect(Collectors.groupingBy(
                                EquipmentAttributeDefinition::getEquipmentTypeId,
                                Collectors.toMap(EquipmentAttributeDefinition::getKey, Function.identity(), (a, b) -> a)
                        ));
        Map<UUID, Map<UUID, EquipmentAttributeValue>> valuesByEquipmentId = new HashMap<>();
        for (EquipmentAttributeValue value :
                attributeValueRepository.findAllByEquipmentIdInAndIsDeletedFalse(Set.of(equipment.getId()))) {
            valuesByEquipmentId
                    .computeIfAbsent(value.getEquipmentId(), ignored -> new HashMap<>())
                    .put(value.getAttributeDefinitionId(), value);
        }
        return new AttributeIndex(definitionsByTypeId, valuesByEquipmentId);
    }

    private boolean matchesConditions(Equipment equipment,
                                      List<MaintenanceRegulationAttributeCondition> conditions,
                                      AttributeIndex attributeIndex) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        Map<String, EquipmentAttributeDefinition> definitions = attributeIndex.definitionsByTypeId()
                .getOrDefault(equipment.getEquipmentTypeId(), Map.of());
        Map<UUID, EquipmentAttributeValue> values = attributeIndex.valuesByEquipmentId()
                .getOrDefault(equipment.getId(), Map.of());
        for (MaintenanceRegulationAttributeCondition condition : conditions) {
            EquipmentAttributeDefinition definition = definitions.get(condition.getAttributeKey());
            EquipmentAttributeValue value = definition == null ? null : values.get(definition.getId());
            if (!matchesCondition(condition, value)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCondition(MaintenanceRegulationAttributeCondition condition,
                                     EquipmentAttributeValue value) {
        MaintenanceRegulationConditionOperator operator = condition.getOperator();
        if (operator == MaintenanceRegulationConditionOperator.EXISTS) {
            return value != null && hasAnyValue(value);
        }
        if (operator == MaintenanceRegulationConditionOperator.NOT_EXISTS) {
            return value == null || !hasAnyValue(value);
        }
        if (value == null || !hasAnyValue(value)) {
            return false;
        }
        if (condition.getValueNumber() != null) {
            return compareNumbers(value.getValueNumber(), condition.getValueNumber(), operator);
        }
        if (condition.getValueDate() != null) {
            return compareComparable(value.getValueDate(), condition.getValueDate(), operator);
        }
        if (condition.getValueBoolean() != null) {
            return compareEquals(value.getValueBoolean(), condition.getValueBoolean(), operator);
        }
        if (condition.getValueOption() != null) {
            return compareEquals(value.getValueOption(), condition.getValueOption(), operator);
        }
        if (condition.getValueText() != null) {
            return compareEquals(value.getValueText(), condition.getValueText(), operator);
        }
        return false;
    }

    private boolean hasAnyValue(EquipmentAttributeValue value) {
        return value.getValueText() != null
                || value.getValueNumber() != null
                || value.getValueDate() != null
                || value.getValueBoolean() != null
                || value.getValueOption() != null
                || value.getValueJson() != null;
    }

    private boolean compareNumbers(Double actual,
                                   Double expected,
                                   MaintenanceRegulationConditionOperator operator) {
        if (actual == null) {
            return false;
        }
        return switch (operator) {
            case EQUALS -> Double.compare(actual, expected) == 0;
            case NOT_EQUALS -> Double.compare(actual, expected) != 0;
            case GREATER_THAN -> actual > expected;
            case GREATER_THAN_OR_EQUALS -> actual >= expected;
            case LESS_THAN -> actual < expected;
            case LESS_THAN_OR_EQUALS -> actual <= expected;
            case EXISTS, NOT_EXISTS -> false;
        };
    }

    private <T extends Comparable<T>> boolean compareComparable(T actual,
                                                                T expected,
                                                                MaintenanceRegulationConditionOperator operator) {
        if (actual == null) {
            return false;
        }
        int comparison = actual.compareTo(expected);
        return switch (operator) {
            case EQUALS -> comparison == 0;
            case NOT_EQUALS -> comparison != 0;
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUALS -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUALS -> comparison <= 0;
            case EXISTS, NOT_EXISTS -> false;
        };
    }

    private boolean compareEquals(Object actual,
                                  Object expected,
                                  MaintenanceRegulationConditionOperator operator) {
        if (operator == MaintenanceRegulationConditionOperator.EQUALS) {
            return Objects.equals(actual, expected);
        }
        if (operator == MaintenanceRegulationConditionOperator.NOT_EQUALS) {
            return !Objects.equals(actual, expected);
        }
        return false;
    }

    private EffectiveMaintenanceRuleDto fromRegulation(UUID equipmentId, MaintenanceRegulation regulation) {
        return new EffectiveMaintenanceRuleDto(
                regulation.getId(),
                regulation.getId(),
                null,
                equipmentId,
                regulation.getId(),
                regulation.getTemplateId(),
                regulation.getCode(),
                regulation.getName(),
                regulation.getDescription(),
                regulation.getMaintenanceKind(),
                regulation.getNormativeLaborHours(),
                regulation.isActive(),
                regulation.getPeriodicityUnit(),
                regulation.getPeriodicityValue(),
                regulation.getToleranceDays(),
                regulation.isRequiresShutdown(),
                regulation.getTriggerMeterType(),
                regulation.getTriggerMeterInterval(),
                regulation.getTriggerPolicy() == null ? MaintenanceTriggerPolicy.ANY : regulation.getTriggerPolicy(),
                regulation.getRecalculationPolicy() == null
                        ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                        : regulation.getRecalculationPolicy(),
                MaintenanceRuleOrigin.TYPE_REGULATION,
                null,
                maintenanceDueCalculationService.calculate(equipmentId, regulation)
        );
    }

    private EffectiveMaintenanceRuleDto fromRule(EquipmentMaintenanceRule rule, MaintenanceRuleOrigin origin) {
        return new EffectiveMaintenanceRuleDto(
                rule.getId(),
                rule.getBaseRegulationId(),
                rule.getId(),
                rule.getEquipmentId(),
                rule.getBaseRegulationId(),
                rule.getTemplateId(),
                rule.getCode(),
                rule.getName(),
                rule.getDescription(),
                rule.getMaintenanceKind(),
                rule.getNormativeLaborHours(),
                rule.isActive(),
                rule.getPeriodicityUnit(),
                rule.getPeriodicityValue(),
                rule.getToleranceDays(),
                rule.isRequiresShutdown(),
                rule.getTriggerMeterType(),
                rule.getTriggerMeterInterval(),
                rule.getTriggerPolicy() == null ? MaintenanceTriggerPolicy.ANY : rule.getTriggerPolicy(),
                rule.getRecalculationPolicy() == null
                        ? MaintenanceRecalculationPolicy.FROM_ACTUAL_COMPLETION
                        : rule.getRecalculationPolicy(),
                origin,
                rule.getOverrideReason(),
                maintenanceDueCalculationService.calculate(rule.getEquipmentId(), rule)
        );
    }

    private String generateCode() {
        int year = Year.now().getValue();
        String prefix = "EMR-" + year + "-";
        long sequence = ruleRepository.maxSequenceByCodePrefix(prefix) + 1;
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = "%s%04d".formatted(prefix, sequence + attempt);
            if (ruleRepository.existsByCodeAndIsDeletedFalse(code)) {
                continue;
            }
            return code;
        }
        throw new DataIntegrityViolationException("Could not generate unique equipment maintenance rule code");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record AttributeIndex(
            Map<UUID, Map<String, EquipmentAttributeDefinition>> definitionsByTypeId,
            Map<UUID, Map<UUID, EquipmentAttributeValue>> valuesByEquipmentId
    ) {}
}

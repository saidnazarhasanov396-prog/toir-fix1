package com.toir.service.maintanance;

import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.entity.maintenance.EquipmentMaintenanceRule;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import com.toir.enums.MaintenanceRegulationConditionOperator;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.maintenance.EquipmentMaintenanceRuleRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import com.toir.repository.maintenance.MaintenanceRegulationRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EquipmentMaintenanceEffectiveRuleResolver {

    private final EquipmentRepository equipmentRepository;
    private final MaintenanceRegulationRepository regulationRepository;
    private final EquipmentMaintenanceRuleRepository ruleRepository;
    private final MaintenanceRegulationAttributeConditionRepository conditionRepository;
    private final EquipmentAttributeDefinitionRepository attributeDefinitionRepository;
    private final EquipmentAttributeValueRepository attributeValueRepository;

    @Transactional(readOnly = true)
    public List<EquipmentMaintenanceEffectiveRule> resolveApplicable(UUID equipmentId) {
        return resolve(equipmentId).stream()
                .filter(EquipmentMaintenanceEffectiveRule::applicable)
                .filter(EquipmentMaintenanceEffectiveRule::active)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EquipmentMaintenanceEffectiveRule> resolve(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        if (equipment.getEquipmentTypeId() == null) {
            return List.of();
        }

        List<MaintenanceRegulation> inheritedRegulations = regulationRepository
                .findAllByEquipmentTypeIdAndActiveTrueAndIsDeletedFalse(equipment.getEquipmentTypeId());
        List<EquipmentMaintenanceRule> individualRules = ruleRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId);
        Map<UUID, EquipmentMaintenanceRule> overrideByBaseId = individualRules.stream()
                .filter(EquipmentMaintenanceRule::isActive)
                .filter(rule -> rule.getBaseRegulationId() != null)
                .collect(Collectors.toMap(
                        EquipmentMaintenanceRule::getBaseRegulationId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<UUID, List<MaintenanceRegulationAttributeCondition>> conditionsByRegulationId =
                loadConditionsByRegulationId(inheritedRegulations);
        AttributeIndex attributeIndex = loadAttributeIndex(equipment);

        List<EquipmentMaintenanceEffectiveRule> resolved = new java.util.ArrayList<>();
        for (MaintenanceRegulation regulation : inheritedRegulations) {
            List<MaintenanceRegulationAttributeCondition> conditions =
                    conditionsByRegulationId.getOrDefault(regulation.getId(), List.of());
            if (!matchesConditions(equipment, conditions, attributeIndex)) {
                resolved.add(EquipmentMaintenanceEffectiveRule.excludedRegulation(
                        equipment.getId(),
                        regulation,
                        "Excluded by attribute conditions"
                ));
                continue;
            }

            EquipmentMaintenanceRule override = overrideByBaseId.get(regulation.getId());
            if (override == null) {
                resolved.add(EquipmentMaintenanceEffectiveRule.fromRegulation(equipment.getId(), regulation));
            } else if (override.isDisablesBaseRegulation()) {
                resolved.add(EquipmentMaintenanceEffectiveRule.disabledByOverride(equipment.getId(), regulation, override));
            } else {
                resolved.add(EquipmentMaintenanceEffectiveRule.fromOverride(equipment.getId(), regulation, override));
            }
        }

        individualRules.stream()
                .filter(EquipmentMaintenanceRule::isActive)
                .filter(rule -> rule.getBaseRegulationId() == null)
                .map(EquipmentMaintenanceEffectiveRule::fromIndividual)
                .forEach(resolved::add);

        return resolved;
    }

    private Map<UUID, List<MaintenanceRegulationAttributeCondition>> loadConditionsByRegulationId(
            List<MaintenanceRegulation> regulations) {
        if (regulations == null || regulations.isEmpty()) {
            return Map.of();
        }
        List<UUID> regulationIds = regulations.stream()
                .map(MaintenanceRegulation::getId)
                .filter(Objects::nonNull)
                .toList();
        if (regulationIds.isEmpty()) {
            return Map.of();
        }
        return conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(regulationIds)
                .stream()
                .collect(Collectors.groupingBy(MaintenanceRegulationAttributeCondition::getRegulationId));
    }

    private AttributeIndex loadAttributeIndex(Equipment equipment) {
        UUID equipmentTypeId = equipment.getEquipmentTypeId();
        UUID equipmentId = equipment.getId();
        if (equipmentTypeId == null || equipmentId == null) {
            return new AttributeIndex(Map.of(), Map.of());
        }
        Map<UUID, Map<String, EquipmentAttributeDefinition>> definitionsByTypeId =
                attributeDefinitionRepository.findAllByEquipmentTypeIdInAndIsDeletedFalse(Set.of(equipmentTypeId))
                        .stream()
                        .collect(Collectors.groupingBy(
                                EquipmentAttributeDefinition::getEquipmentTypeId,
                                Collectors.toMap(EquipmentAttributeDefinition::getKey, Function.identity(), (a, b) -> a)
                        ));
        Map<UUID, Map<UUID, EquipmentAttributeValue>> valuesByEquipmentId = new HashMap<>();
        for (EquipmentAttributeValue value : attributeValueRepository.findAllByEquipmentIdInAndIsDeletedFalse(Set.of(equipmentId))) {
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

    private record AttributeIndex(
            Map<UUID, Map<String, EquipmentAttributeDefinition>> definitionsByTypeId,
            Map<UUID, Map<UUID, EquipmentAttributeValue>> valuesByEquipmentId
    ) {}
}

package com.toir.service.maintanance;

import com.toir.dto.maintenanceplanning.MaintenanceDueCalculationDto;
import com.toir.dto.maintenanceregulation.MaintenanceRegulationAttributeConditionRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.entity.maintenance.MaintenanceRegulation;
import com.toir.entity.maintenance.MaintenanceRegulationAttributeCondition;
import com.toir.enums.MaintenanceDueStatus;
import com.toir.enums.MaintenanceRegulationConditionOperator;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.maintenance.MaintenanceRegulationAttributeConditionRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MaintenanceRegulationApplicabilityService {

    private final MaintenanceRegulationAttributeConditionRepository conditionRepository;
    private final EquipmentAttributeDefinitionRepository attributeDefinitionRepository;
    private final EquipmentAttributeValueRepository attributeValueRepository;

    public ApplicabilityResult evaluate(Equipment equipment,
                                        MaintenanceRegulation regulation,
                                        List<MaintenanceRegulationAttributeConditionRequest> requestConditions,
                                        MaintenanceDueCalculationDto due) {
        ConditionMatch conditionMatch = matchesConditions(equipment, requestConditions, persistedConditions(regulation));
        if (!conditionMatch.matched()) {
            return new ApplicabilityResult(false, false, false, conditionMatch.reason(), null,
                    "EXCLUDED_BY_ATTRIBUTE", conditionMatch.attributeKey());
        }
        boolean blocked = due != null && due.status() == MaintenanceDueStatus.BLOCKED;
        boolean missingMeter = blocked
                && due.structuredExplanation() != null
                && "MISSING_ACTIVE_METER".equals(due.structuredExplanation().blockingCode());
        return new ApplicabilityResult(
                true,
                blocked,
                missingMeter,
                due == null ? null : due.explanation(),
                due == null ? null : due.status(),
                null,
                null
        );
    }

    public boolean matchesPersistedConditions(Equipment equipment, MaintenanceRegulation regulation) {
        return matchesConditions(equipment, null, persistedConditions(regulation)).matched();
    }

    public record ApplicabilityResult(
            boolean matched,
            boolean blocked,
            boolean missingMeter,
            String reason,
            MaintenanceDueStatus dueStatus,
            String reasonCode,
            String attributeKey
    ) {}

    private List<MaintenanceRegulationAttributeCondition> persistedConditions(MaintenanceRegulation regulation) {
        if (regulation == null || regulation.getId() == null) {
            return List.of();
        }
        return conditionRepository.findAllByRegulationIdInAndIsDeletedFalse(List.of(regulation.getId()));
    }

    private ConditionMatch matchesConditions(Equipment equipment,
                                             List<MaintenanceRegulationAttributeConditionRequest> requestConditions,
                                             List<MaintenanceRegulationAttributeCondition> persistedConditions) {
        if ((requestConditions == null || requestConditions.isEmpty())
                && (persistedConditions == null || persistedConditions.isEmpty())) {
            return ConditionMatch.success();
        }
        AttributeIndex attributeIndex = loadAttributeIndex(equipment);
        if (requestConditions != null && !requestConditions.isEmpty()) {
            for (MaintenanceRegulationAttributeConditionRequest condition : requestConditions) {
                if (!matchesCondition(equipment, attributeIndex, condition)) {
                    return ConditionMatch.unmatched(condition.attributeKey());
                }
            }
            return ConditionMatch.success();
        }
        for (MaintenanceRegulationAttributeCondition condition : persistedConditions) {
            if (!matchesCondition(equipment, attributeIndex, condition)) {
                return ConditionMatch.unmatched(condition.getAttributeKey());
            }
        }
        return ConditionMatch.success();
    }

    private AttributeIndex loadAttributeIndex(Equipment equipment) {
        UUID equipmentTypeId = equipment.getEquipmentTypeId();
        UUID equipmentId = equipment.getId();
        if (equipmentTypeId == null || equipmentId == null) {
            return new AttributeIndex(Map.of(), Map.of());
        }
        Map<String, EquipmentAttributeDefinition> definitions =
                attributeDefinitionRepository.findAllByEquipmentTypeIdInAndIsDeletedFalse(Set.of(equipmentTypeId))
                        .stream()
                        .collect(Collectors.toMap(EquipmentAttributeDefinition::getKey, Function.identity(), (a, b) -> a));
        Map<UUID, EquipmentAttributeValue> values =
                attributeValueRepository.findAllByEquipmentIdInAndIsDeletedFalse(Set.of(equipmentId))
                        .stream()
                        .collect(Collectors.toMap(EquipmentAttributeValue::getAttributeDefinitionId, Function.identity(), (a, b) -> a));
        return new AttributeIndex(definitions, values);
    }

    private boolean matchesCondition(Equipment equipment,
                                     AttributeIndex attributeIndex,
                                     MaintenanceRegulationAttributeConditionRequest condition) {
        EquipmentAttributeDefinition definition = attributeIndex.definitions().get(normalizeKey(condition.attributeKey()));
        EquipmentAttributeValue value = definition == null ? null : attributeIndex.values().get(definition.getId());
        return matchesValue(
                condition.operator(),
                value,
                condition.valueText(),
                condition.valueNumber(),
                condition.valueDate(),
                condition.valueBoolean(),
                condition.valueOption()
        );
    }

    private boolean matchesCondition(Equipment equipment,
                                     AttributeIndex attributeIndex,
                                     MaintenanceRegulationAttributeCondition condition) {
        EquipmentAttributeDefinition definition = attributeIndex.definitions().get(normalizeKey(condition.getAttributeKey()));
        EquipmentAttributeValue value = definition == null ? null : attributeIndex.values().get(definition.getId());
        return matchesValue(
                condition.getOperator(),
                value,
                condition.getValueText(),
                condition.getValueNumber(),
                condition.getValueDate(),
                condition.getValueBoolean(),
                condition.getValueOption()
        );
    }

    private boolean matchesValue(MaintenanceRegulationConditionOperator operator,
                                 EquipmentAttributeValue value,
                                 String valueText,
                                 Double valueNumber,
                                 java.time.LocalDate valueDate,
                                 Boolean valueBoolean,
                                 String valueOption) {
        if (operator == MaintenanceRegulationConditionOperator.EXISTS) {
            return value != null && hasAnyValue(value);
        }
        if (operator == MaintenanceRegulationConditionOperator.NOT_EXISTS) {
            return value == null || !hasAnyValue(value);
        }
        if (value == null || !hasAnyValue(value)) {
            return false;
        }
        if (valueNumber != null) {
            return compareNumbers(value.getValueNumber(), valueNumber, operator);
        }
        if (valueDate != null) {
            return compareComparable(value.getValueDate(), valueDate, operator);
        }
        if (valueBoolean != null) {
            return compareEquals(value.getValueBoolean(), valueBoolean, operator);
        }
        if (valueOption != null) {
            return compareEquals(value.getValueOption(), valueOption, operator);
        }
        if (valueText != null) {
            return compareEquals(value.getValueText(), valueText, operator);
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

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private record AttributeIndex(
            Map<String, EquipmentAttributeDefinition> definitions,
            Map<UUID, EquipmentAttributeValue> values
    ) {}

    private record ConditionMatch(boolean matched, String reason, String attributeKey) {
        static ConditionMatch success() {
            return new ConditionMatch(true, null, null);
        }

        static ConditionMatch unmatched(String attributeKey) {
            return new ConditionMatch(false, "Excluded by attribute condition: " + attributeKey, attributeKey);
        }
    }
}

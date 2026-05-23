package com.toir.service.equipment;

import com.toir.dto.equipmentattribute.EquipmentAttributeOptionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest;
import com.toir.dto.equipmentattribute.EquipmentAttributeOptionSourceDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeOptionSourceRequest;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueHistoryDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeOptionItem;
import com.toir.entity.equipment.EquipmentAttributeOptionSource;
import com.toir.entity.equipment.EquipmentAttributeRequiredCriticality;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.entity.equipment.EquipmentAttributeValueHistory;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.enums.EquipmentAttributeValueHistorySource;
import com.toir.exception.RestException;
import com.toir.repository.CriticalityClassRepository;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeOptionItemRepository;
import com.toir.repository.equipment.EquipmentAttributeOptionSourceRepository;
import com.toir.repository.equipment.EquipmentAttributeRequiredCriticalityRepository;
import com.toir.repository.equipment.EquipmentAttributeValueHistoryRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EquipmentAttributeService {

    private final EquipmentAttributeDefinitionRepository definitionRepository;
    private final EquipmentAttributeValueRepository valueRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentAttributeOptionSourceRepository optionSourceRepository;
    private final EquipmentAttributeOptionItemRepository optionItemRepository;
    private final EquipmentAttributeRequiredCriticalityRepository requiredCriticalityRepository;
    private final CriticalityClassRepository criticalityClassRepository;
    private final EquipmentAttributeValueHistoryRepository valueHistoryRepository;

    @Transactional(readOnly = true)
    public List<EquipmentAttributeOptionSourceDto> findOptionSources() {
        return optionSourceRepository.findAllByIsDeletedFalseOrderByCodeAsc()
                .stream()
                .map(EquipmentAttributeOptionSourceDto::from)
                .toList();
    }

    @Transactional
    public EquipmentAttributeOptionSourceDto createOptionSource(EquipmentAttributeOptionSourceRequest request) {
        String code = normalizeKey(request.code());
        if (optionSourceRepository.existsByCodeAndIsDeletedFalse(code)) {
            throw RestException.conflict("Equipment attribute option source already exists: " + code);
        }
        EquipmentAttributeOptionSource source = new EquipmentAttributeOptionSource();
        source.setCode(code);
        source.setName(request.name());
        source.setNameRu(request.nameRu());
        source.setNameUz(request.nameUz());
        source.setDescription(request.description());
        return EquipmentAttributeOptionSourceDto.from(optionSourceRepository.save(source));
    }

    @Transactional(readOnly = true)
    public List<EquipmentAttributeOptionDto> findOptions(UUID sourceId) {
        ensureOptionSourceExists(sourceId);
        return optionItemRepository.findAllBySourceIdAndIsDeletedFalse(sourceId)
                .stream()
                .map(this::toOptionDto)
                .toList();
    }

    @Transactional
    public List<EquipmentAttributeOptionDto> replaceOptions(UUID sourceId, List<EquipmentAttributeOptionDto> options) {
        ensureOptionSourceExists(sourceId);
        List<EquipmentAttributeOptionItem> existing = optionItemRepository.findAllBySourceIdAndIsDeletedFalse(sourceId);
        for (EquipmentAttributeOptionItem item : existing) {
            item.setDeleted(true);
        }
        if (!existing.isEmpty()) {
            optionItemRepository.saveAll(existing);
        }
        List<EquipmentAttributeOptionItem> toSave = (options == null ? List.<EquipmentAttributeOptionDto>of() : options)
                .stream()
                .map(option -> toOptionItem(sourceId, option))
                .toList();
        if (!toSave.isEmpty()) {
            optionItemRepository.saveAll(toSave);
        }
        return findOptions(sourceId);
    }

    @Transactional(readOnly = true)
    public List<EquipmentAttributeDefinitionDto> findDefinitions(UUID equipmentTypeId) {
        ensureEquipmentTypeExists(equipmentTypeId);
        List<EquipmentAttributeDefinition> definitions =
                definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(equipmentTypeId);
        Map<UUID, List<UUID>> requiredByCriticality = requiredCriticalityByDefinitionId(definitions);
        return definitions.stream()
                .map(definition -> EquipmentAttributeDefinitionDto.from(
                        definition,
                        requiredByCriticality.getOrDefault(definition.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public EquipmentAttributeDefinitionDto createDefinition(UUID equipmentTypeId,
                                                           EquipmentAttributeDefinitionRequest request) {
        ensureEquipmentTypeExists(equipmentTypeId);
        String key = normalizeKey(request.key());
        if (definitionRepository.existsActiveByEquipmentTypeIdAndKey(equipmentTypeId, key)) {
            throw RestException.conflict("Equipment attribute definition already exists: " + key);
        }
        EquipmentAttributeDefinition definition = new EquipmentAttributeDefinition();
        definition.setEquipmentTypeId(equipmentTypeId);
        applyDefinition(definition, request, key);
        List<UUID> requiredCriticalityClassIds = uniqueIds(request.normalizedRequiredForCriticalityClassIds());
        validateRequiredCriticalityClassIds(requiredCriticalityClassIds);
        EquipmentAttributeDefinition saved = definitionRepository.save(definition);
        replaceRequiredCriticalities(saved.getId(), requiredCriticalityClassIds);
        return EquipmentAttributeDefinitionDto.from(saved, requiredCriticalityClassIds);
    }

    @Transactional
    public EquipmentAttributeDefinitionDto updateDefinition(UUID equipmentTypeId,
                                                           UUID definitionId,
                                                           EquipmentAttributeDefinitionRequest request) {
        ensureEquipmentTypeExists(equipmentTypeId);
        EquipmentAttributeDefinition definition = getDefinitionOrThrow(definitionId);
        if (!Objects.equals(definition.getEquipmentTypeId(), equipmentTypeId)) {
            throw RestException.badRequest("Attribute definition does not belong to equipment type: " + equipmentTypeId);
        }
        String key = normalizeKey(request.key());
        if (!key.equals(definition.getKey())
                && definitionRepository.existsActiveByEquipmentTypeIdAndKey(equipmentTypeId, key)) {
            throw RestException.conflict("Equipment attribute definition already exists: " + key);
        }
        applyDefinition(definition, request, key);
        List<UUID> requiredCriticalityClassIds = uniqueIds(request.normalizedRequiredForCriticalityClassIds());
        validateRequiredCriticalityClassIds(requiredCriticalityClassIds);
        EquipmentAttributeDefinition saved = definitionRepository.save(definition);
        replaceRequiredCriticalities(saved.getId(), requiredCriticalityClassIds);
        return EquipmentAttributeDefinitionDto.from(saved, requiredCriticalityClassIds);
    }

    @Transactional
    public void deleteDefinition(UUID equipmentTypeId, UUID definitionId) {
        ensureEquipmentTypeExists(equipmentTypeId);
        EquipmentAttributeDefinition definition = getDefinitionOrThrow(definitionId);
        if (!Objects.equals(definition.getEquipmentTypeId(), equipmentTypeId)) {
            throw RestException.badRequest("Attribute definition does not belong to equipment type: " + equipmentTypeId);
        }
        definition.setDeleted(true);
        requiredCriticalityRepository.softDeleteByAttributeDefinitionId(definitionId);
        definitionRepository.save(definition);
    }

    @Transactional(readOnly = true)
    public List<EquipmentAttributeValueDto> findValues(UUID equipmentId) {
        Equipment equipment = getEquipmentOrThrow(equipmentId);
        return buildValueDtos(equipment, valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId));
    }

    @Transactional(readOnly = true)
    public Page<EquipmentAttributeValueHistoryDto> findValueHistory(UUID equipmentId,
                                                                    UUID attributeDefinitionId,
                                                                    Pageable pageable) {
        getEquipmentOrThrow(equipmentId);
        Page<EquipmentAttributeValueHistory> history = attributeDefinitionId == null
                ? valueHistoryRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByChangedAtDesc(equipmentId, pageable)
                : valueHistoryRepository.findAllByEquipmentIdAndAttributeDefinitionIdAndIsDeletedFalseOrderByChangedAtDesc(
                        equipmentId,
                        attributeDefinitionId,
                        pageable
                );
        return history.map(EquipmentAttributeValueHistoryDto::from);
    }

    @Transactional
    public List<EquipmentAttributeValueDto> replaceValues(UUID equipmentId,
                                                         List<EquipmentAttributeValueRequest> requests) {
        Equipment equipment = getEquipmentOrThrow(equipmentId);
        upsertValues(equipment, requests);
        return findValues(equipmentId);
    }

    @Transactional
    public void upsertValues(Equipment equipment, List<EquipmentAttributeValueRequest> requests) {
        if (requests == null) {
            return;
        }
        List<EquipmentAttributeDefinition> definitions = definitionRepository
                .findAllByEquipmentTypeIdAndIsDeletedFalse(equipment.getEquipmentTypeId());
        Map<UUID, EquipmentAttributeDefinition> byId = definitions.stream()
                .collect(Collectors.toMap(EquipmentAttributeDefinition::getId, Function.identity(), (a, b) -> a));
        Map<String, EquipmentAttributeDefinition> byKey = definitions.stream()
                .collect(Collectors.toMap(EquipmentAttributeDefinition::getKey, Function.identity(), (a, b) -> a));
        Map<UUID, EquipmentAttributeValue> existing = valueRepository
                .findAllByEquipmentIdAndIsDeletedFalse(equipment.getId())
                .stream()
                .collect(Collectors.toMap(EquipmentAttributeValue::getAttributeDefinitionId, Function.identity(), (a, b) -> a));

        Map<UUID, EquipmentAttributeValueRequest> requestByDefinitionId = new HashMap<>();
        for (EquipmentAttributeValueRequest request : requests) {
            EquipmentAttributeDefinition definition = resolveDefinition(request, byId, byKey);
            validateValue(definition, request);
            if (requestByDefinitionId.put(definition.getId(), request) != null) {
                throw RestException.badRequest("Duplicate equipment attribute value: " + definition.getKey());
            }
        }
        validateRequired(equipment, definitions, existing, requestByDefinitionId);

        List<EquipmentAttributeValue> toSave = new ArrayList<>();
        List<EquipmentAttributeValueHistory> historyToSave = new ArrayList<>();
        for (Map.Entry<UUID, EquipmentAttributeValueRequest> entry : requestByDefinitionId.entrySet()) {
            EquipmentAttributeDefinition definition = byId.get(entry.getKey());
            EquipmentAttributeValue currentValue = existing.get(entry.getKey());
            String oldValue = serializeValue(definition, currentValue);
            String newValue = serializeRequestValue(definition, entry.getValue());
            EquipmentAttributeValue value = existing.getOrDefault(entry.getKey(), new EquipmentAttributeValue());
            value.setEquipmentId(equipment.getId());
            value.setAttributeDefinitionId(entry.getKey());
            applyValue(value, entry.getValue());
            toSave.add(value);
            if (!Objects.equals(oldValue, newValue)) {
                historyToSave.add(historyEntry(equipment, definition, oldValue, newValue));
            }
        }
        if (!toSave.isEmpty()) {
            valueRepository.saveAll(toSave);
        }
        if (!historyToSave.isEmpty()) {
            valueHistoryRepository.saveAll(historyToSave);
        }
    }

    private EquipmentAttributeValueHistory historyEntry(Equipment equipment,
                                                       EquipmentAttributeDefinition definition,
                                                       String oldValue,
                                                       String newValue) {
        EquipmentAttributeValueHistory history = new EquipmentAttributeValueHistory();
        history.setEquipmentId(equipment.getId());
        history.setAttributeDefinitionId(definition.getId());
        history.setAttributeKey(definition.getKey());
        history.setAttributeLabel(definition.getLabel());
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setChangedAt(Instant.now());
        history.setSource(EquipmentAttributeValueHistorySource.API);
        return history;
    }

    private String serializeValue(EquipmentAttributeDefinition definition, EquipmentAttributeValue value) {
        if (value == null) {
            return null;
        }
        return switch (definition.getDataType()) {
            case TEXT, FILE, REFERENCE -> normalizeStringValue(value.getValueText());
            case NUMBER, RANGE -> normalizeNumberValue(value.getValueNumber());
            case DATE -> value.getValueDate() == null ? null : value.getValueDate().toString();
            case BOOLEAN -> value.getValueBoolean() == null ? null : value.getValueBoolean().toString();
            case SELECT -> normalizeStringValue(value.getValueOption());
            case MULTI_SELECT, JSON -> normalizeStringValue(value.getValueJson());
        };
    }

    private String serializeRequestValue(EquipmentAttributeDefinition definition, EquipmentAttributeValueRequest request) {
        return switch (definition.getDataType()) {
            case TEXT, FILE, REFERENCE -> normalizeStringValue(request.valueText());
            case NUMBER, RANGE -> normalizeNumberValue(request.valueNumber());
            case DATE -> request.valueDate() == null ? null : request.valueDate().toString();
            case BOOLEAN -> request.valueBoolean() == null ? null : request.valueBoolean().toString();
            case SELECT -> normalizeStringValue(request.valueOption());
            case MULTI_SELECT, JSON -> normalizeStringValue(request.valueJson());
        };
    }

    private String normalizeNumberValue(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String normalizeStringValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<EquipmentAttributeValueDto> buildValueDtos(Equipment equipment,
                                                           List<EquipmentAttributeValue> values) {
        List<EquipmentAttributeDefinition> definitions = definitionRepository
                .findAllByEquipmentTypeIdAndIsDeletedFalse(equipment.getEquipmentTypeId());
        Map<UUID, EquipmentAttributeValue> valuesByDefinitionId = values.stream()
                .collect(Collectors.toMap(EquipmentAttributeValue::getAttributeDefinitionId, Function.identity(), (a, b) -> a));
        return definitions.stream()
                .map(definition -> EquipmentAttributeValueDto.from(
                        definition,
                        valuesByDefinitionId.get(definition.getId()),
                        equipment.getId()
                ))
                .toList();
    }

    private void applyDefinition(EquipmentAttributeDefinition definition,
                                 EquipmentAttributeDefinitionRequest request,
                                 String key) {
        if (request.minValue() != null && request.maxValue() != null && request.minValue() > request.maxValue()) {
            throw RestException.badRequest("minValue cannot be greater than maxValue");
        }
        definition.setKey(key);
        definition.setLabel(request.label());
        definition.setLabelRu(request.labelRu());
        definition.setLabelUz(request.labelUz());
        definition.setDataType(request.dataType());
        definition.setUnit(request.unit());
        definition.setRequired(request.required());
        definition.setMinValue(request.minValue());
        definition.setMaxValue(request.maxValue());
        definition.setOptionSourceId(request.optionSourceId());
        if (request.optionSourceId() != null) {
            ensureOptionSourceExists(request.optionSourceId());
        }
        definition.setOptions(request.options() == null ? List.of() : request.options());
        definition.setGroupName(request.groupName());
        definition.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private EquipmentAttributeDefinition resolveDefinition(EquipmentAttributeValueRequest request,
                                                          Map<UUID, EquipmentAttributeDefinition> byId,
                                                          Map<String, EquipmentAttributeDefinition> byKey) {
        if (request == null) {
            throw RestException.badRequest("Attribute value request is required");
        }
        if (request.attributeDefinitionId() != null) {
            EquipmentAttributeDefinition definition = byId.get(request.attributeDefinitionId());
            if (definition == null) {
                throw RestException.badRequest("Attribute definition is not allowed for this equipment type: " + request.attributeDefinitionId());
            }
            return definition;
        }
        String key = normalizeKey(request.key());
        EquipmentAttributeDefinition definition = byKey.get(key);
        if (definition == null) {
            throw RestException.badRequest("Unknown equipment attribute key: " + key);
        }
        return definition;
    }

    private void validateRequired(Equipment equipment,
                                  List<EquipmentAttributeDefinition> definitions,
                                  Map<UUID, EquipmentAttributeValue> existing,
                                  Map<UUID, EquipmentAttributeValueRequest> requestByDefinitionId) {
        Set<String> missing = new HashSet<>();
        Map<UUID, List<UUID>> requiredByCriticality = requiredCriticalityByDefinitionId(definitions);
        for (EquipmentAttributeDefinition definition : definitions) {
            String reason = requiredReason(equipment, definition, requiredByCriticality);
            if (reason == null) {
                continue;
            }
            EquipmentAttributeValueRequest request = requestByDefinitionId.get(definition.getId());
            if (request != null) {
                if (isBlankForType(definition.getDataType(), request)) {
                    missing.add(missingLabel(definition, reason));
                }
                continue;
            }
            EquipmentAttributeValue current = existing.get(definition.getId());
            if (current == null || isBlankValue(current)) {
                missing.add(missingLabel(definition, reason));
            }
        }
        if (!missing.isEmpty()) {
            throw RestException.badRequest("Missing required equipment attributes: " + String.join(", ", missing));
        }
    }

    private String requiredReason(Equipment equipment,
                                  EquipmentAttributeDefinition definition,
                                  Map<UUID, List<UUID>> requiredByCriticality) {
        if (definition.isRequired()) {
            return "required by equipment type";
        }
        UUID criticalityClassId = equipment.getCriticalityClassId();
        if (criticalityClassId == null) {
            return null;
        }
        List<UUID> requiredCriticalityIds = requiredByCriticality.getOrDefault(definition.getId(), List.of());
        return requiredCriticalityIds.contains(criticalityClassId) ? "required by criticality" : null;
    }

    private String missingLabel(EquipmentAttributeDefinition definition, String reason) {
        return definition.getKey() + " (" + reason + ")";
    }

    private void validateValue(EquipmentAttributeDefinition definition, EquipmentAttributeValueRequest request) {
        EquipmentAttributeDataType dataType = definition.getDataType();
        int populatedValueFields = populatedValueFieldCount(request);
        if (populatedValueFields == 0) {
            if (definition.isRequired()) {
                throw RestException.badRequest("Attribute is required: " + definition.getKey());
            }
            return;
        }
        if (populatedValueFields > 1) {
            throw RestException.badRequest("Attribute value must use only one value field: " + definition.getKey());
        }
        validateExpectedValueField(definition, request);
        if (dataType == EquipmentAttributeDataType.NUMBER || dataType == EquipmentAttributeDataType.RANGE) {
            Double value = request.valueNumber();
            if (definition.getMinValue() != null && value < definition.getMinValue()) {
                throw RestException.badRequest("Attribute value is below minValue: " + definition.getKey());
            }
            if (definition.getMaxValue() != null && value > definition.getMaxValue()) {
                throw RestException.badRequest("Attribute value is above maxValue: " + definition.getKey());
            }
            return;
        }
        if (dataType == EquipmentAttributeDataType.SELECT) {
            validateOption(definition, request.valueOption());
        }
        if (dataType == EquipmentAttributeDataType.MULTI_SELECT) {
            if (request.valueJson() == null || request.valueJson().isBlank()) {
                throw RestException.badRequest("Attribute requires valueJson: " + definition.getKey());
            }
        }
    }

    private void validateExpectedValueField(EquipmentAttributeDefinition definition,
                                            EquipmentAttributeValueRequest request) {
        boolean valid = switch (definition.getDataType()) {
            case TEXT, FILE, REFERENCE -> request.valueText() != null && !request.valueText().isBlank();
            case NUMBER, RANGE -> request.valueNumber() != null;
            case DATE -> request.valueDate() != null;
            case BOOLEAN -> request.valueBoolean() != null;
            case SELECT -> request.valueOption() != null && !request.valueOption().isBlank();
            case MULTI_SELECT, JSON -> request.valueJson() != null && !request.valueJson().isBlank();
        };
        if (!valid) {
            throw RestException.badRequest(
                    "Attribute requires " + expectedValueField(definition.getDataType()) + ": " + definition.getKey()
            );
        }
    }

    private String expectedValueField(EquipmentAttributeDataType dataType) {
        return switch (dataType) {
            case TEXT, FILE, REFERENCE -> "valueText";
            case NUMBER, RANGE -> "valueNumber";
            case DATE -> "valueDate";
            case BOOLEAN -> "valueBoolean";
            case SELECT -> "valueOption";
            case MULTI_SELECT, JSON -> "valueJson";
        };
    }

    private int populatedValueFieldCount(EquipmentAttributeValueRequest request) {
        int count = 0;
        if (request.valueText() != null && !request.valueText().isBlank()) count++;
        if (request.valueNumber() != null) count++;
        if (request.valueDate() != null) count++;
        if (request.valueBoolean() != null) count++;
        if (request.valueOption() != null && !request.valueOption().isBlank()) count++;
        if (request.valueJson() != null && !request.valueJson().isBlank()) count++;
        return count;
    }

    private void validateOption(EquipmentAttributeDefinition definition, String value) {
        if (definition.getOptionSourceId() != null) {
            if (!optionItemRepository.existsActiveBySourceIdAndOptionId(definition.getOptionSourceId(), value)) {
                throw RestException.badRequest("Attribute option is not allowed: " + definition.getKey());
            }
            return;
        }
        List<EquipmentAttributeOptionDto> options = definition.getOptions() == null ? List.of() : definition.getOptions();
        if (options.isEmpty()) {
            return;
        }
        boolean allowed = options.stream()
                .filter(option -> option.active() == null || option.active())
                .anyMatch(option -> option.id().equals(value));
        if (!allowed) {
            throw RestException.badRequest("Attribute option is not allowed: " + definition.getKey());
        }
    }

    private boolean isBlankForType(EquipmentAttributeDataType dataType, EquipmentAttributeValueRequest request) {
        return switch (dataType) {
            case TEXT, FILE, REFERENCE -> request.valueText() == null || request.valueText().isBlank();
            case NUMBER, RANGE -> request.valueNumber() == null;
            case DATE -> request.valueDate() == null;
            case BOOLEAN -> request.valueBoolean() == null;
            case SELECT -> request.valueOption() == null || request.valueOption().isBlank();
            case MULTI_SELECT, JSON -> request.valueJson() == null || request.valueJson().isBlank();
        };
    }

    private boolean isBlankValue(EquipmentAttributeValue value) {
        return value.getValueText() == null
                && value.getValueNumber() == null
                && value.getValueDate() == null
                && value.getValueBoolean() == null
                && value.getValueOption() == null
                && value.getValueJson() == null;
    }

    private void validateRequiredCriticalityClassIds(List<UUID> criticalityClassIds) {
        List<UUID> uniqueIds = uniqueIds(criticalityClassIds);
        if (uniqueIds.isEmpty()) {
            return;
        }
        Set<UUID> existingIds = criticalityClassRepository.findAllByIdInAndIsDeletedFalse(uniqueIds)
                .stream()
                .map(com.toir.entity.equipment.CriticalityClass::getId)
                .collect(Collectors.toSet());
        List<UUID> missing = uniqueIds.stream()
                .filter(id -> !existingIds.contains(id))
                .toList();
        if (!missing.isEmpty()) {
            throw RestException.notFound("Criticality class not found: " + missing.getFirst());
        }
    }

    private void replaceRequiredCriticalities(UUID definitionId, List<UUID> criticalityClassIds) {
        List<EquipmentAttributeRequiredCriticality> existing =
                requiredCriticalityRepository.findAllByAttributeDefinitionIdAndIsDeletedFalse(definitionId);
        if (!existing.isEmpty()) {
            for (EquipmentAttributeRequiredCriticality policy : existing) {
                policy.setDeleted(true);
            }
            requiredCriticalityRepository.saveAll(existing);
        }
        List<EquipmentAttributeRequiredCriticality> policies = uniqueIds(criticalityClassIds).stream()
                .map(criticalityClassId -> {
                    EquipmentAttributeRequiredCriticality policy = new EquipmentAttributeRequiredCriticality();
                    policy.setAttributeDefinitionId(definitionId);
                    policy.setCriticalityClassId(criticalityClassId);
                    return policy;
                })
                .toList();
        if (!policies.isEmpty()) {
            requiredCriticalityRepository.saveAll(policies);
        }
    }

    private Map<UUID, List<UUID>> requiredCriticalityByDefinitionId(List<EquipmentAttributeDefinition> definitions) {
        List<UUID> definitionIds = definitions.stream()
                .map(EquipmentAttributeDefinition::getId)
                .filter(Objects::nonNull)
                .toList();
        if (definitionIds.isEmpty()) {
            return Map.of();
        }
        return requiredCriticalityRepository.findAllByAttributeDefinitionIdInAndIsDeletedFalse(definitionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        EquipmentAttributeRequiredCriticality::getAttributeDefinitionId,
                        Collectors.mapping(EquipmentAttributeRequiredCriticality::getCriticalityClassId, Collectors.toList())
                ));
    }

    private List<UUID> uniqueIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }

    private void applyValue(EquipmentAttributeValue value, EquipmentAttributeValueRequest request) {
        value.setValueText(request.valueText());
        value.setValueNumber(request.valueNumber());
        value.setValueDate(request.valueDate());
        value.setValueBoolean(request.valueBoolean());
        value.setValueOption(request.valueOption());
        value.setValueJson(request.valueJson());
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw RestException.badRequest("Attribute key is required");
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private EquipmentAttributeDefinition getDefinitionOrThrow(UUID id) {
        return definitionRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment attribute definition not found: " + id));
    }

    private Equipment getEquipmentOrThrow(UUID id) {
        return equipmentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));
    }

    private void ensureEquipmentTypeExists(UUID id) {
        if (id == null) {
            throw RestException.badRequest("equipmentTypeId is required");
        }
        equipmentTypeRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment type not found: " + id));
    }

    private void ensureOptionSourceExists(UUID id) {
        if (id == null) {
            return;
        }
        optionSourceRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment attribute option source not found: " + id));
    }

    private EquipmentAttributeOptionItem toOptionItem(UUID sourceId, EquipmentAttributeOptionDto option) {
        if (option == null || option.id() == null || option.id().isBlank()) {
            throw RestException.badRequest("Option id is required");
        }
        if (option.label() == null || option.label().isBlank()) {
            throw RestException.badRequest("Option label is required");
        }
        EquipmentAttributeOptionItem item = new EquipmentAttributeOptionItem();
        item.setOptionSourceId(sourceId);
        item.setOptionId(option.id().trim());
        item.setLabel(option.label());
        item.setLabelRu(option.labelRu());
        item.setLabelUz(option.labelUz());
        item.setSortOrder(option.sortOrder() == null ? 0 : option.sortOrder());
        item.setActive(option.active() == null || option.active());
        return item;
    }

    private EquipmentAttributeOptionDto toOptionDto(EquipmentAttributeOptionItem item) {
        return new EquipmentAttributeOptionDto(
                item.getOptionId(),
                item.getLabel(),
                item.getLabelRu(),
                item.getLabelUz(),
                item.getSortOrder(),
                item.isActive()
        );
    }
}

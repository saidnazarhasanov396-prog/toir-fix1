package com.toir.service.equipment;

import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeDefinitionRequest;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueDto;
import com.toir.dto.equipmentattribute.EquipmentAttributeValueRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.enums.EquipmentAttributeDataType;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    @Transactional(readOnly = true)
    public List<EquipmentAttributeDefinitionDto> findDefinitions(UUID equipmentTypeId) {
        ensureEquipmentTypeExists(equipmentTypeId);
        return definitionRepository.findAllByEquipmentTypeIdAndIsDeletedFalse(equipmentTypeId)
                .stream()
                .map(EquipmentAttributeDefinitionDto::from)
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
        return EquipmentAttributeDefinitionDto.from(definitionRepository.save(definition));
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
        return EquipmentAttributeDefinitionDto.from(definitionRepository.save(definition));
    }

    @Transactional
    public void deleteDefinition(UUID equipmentTypeId, UUID definitionId) {
        ensureEquipmentTypeExists(equipmentTypeId);
        EquipmentAttributeDefinition definition = getDefinitionOrThrow(definitionId);
        if (!Objects.equals(definition.getEquipmentTypeId(), equipmentTypeId)) {
            throw RestException.badRequest("Attribute definition does not belong to equipment type: " + equipmentTypeId);
        }
        definition.setDeleted(true);
        definitionRepository.save(definition);
    }

    @Transactional(readOnly = true)
    public List<EquipmentAttributeValueDto> findValues(UUID equipmentId) {
        Equipment equipment = getEquipmentOrThrow(equipmentId);
        return buildValueDtos(equipment, valueRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId));
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
        validateRequired(definitions, existing, requestByDefinitionId);

        List<EquipmentAttributeValue> toSave = new ArrayList<>();
        for (Map.Entry<UUID, EquipmentAttributeValueRequest> entry : requestByDefinitionId.entrySet()) {
            EquipmentAttributeValue value = existing.getOrDefault(entry.getKey(), new EquipmentAttributeValue());
            value.setEquipmentId(equipment.getId());
            value.setAttributeDefinitionId(entry.getKey());
            applyValue(value, entry.getValue());
            toSave.add(value);
        }
        if (!toSave.isEmpty()) {
            valueRepository.saveAll(toSave);
        }
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

    private void validateRequired(List<EquipmentAttributeDefinition> definitions,
                                  Map<UUID, EquipmentAttributeValue> existing,
                                  Map<UUID, EquipmentAttributeValueRequest> requestByDefinitionId) {
        Set<String> missing = new HashSet<>();
        for (EquipmentAttributeDefinition definition : definitions) {
            if (!definition.isRequired()) {
                continue;
            }
            EquipmentAttributeValueRequest request = requestByDefinitionId.get(definition.getId());
            if (request != null) {
                if (isBlankForType(definition.getDataType(), request)) {
                    missing.add(definition.getKey());
                }
                continue;
            }
            EquipmentAttributeValue current = existing.get(definition.getId());
            if (current == null || isBlankValue(current)) {
                missing.add(definition.getKey());
            }
        }
        if (!missing.isEmpty()) {
            throw RestException.badRequest("Missing required equipment attributes: " + String.join(", ", missing));
        }
    }

    private void validateValue(EquipmentAttributeDefinition definition, EquipmentAttributeValueRequest request) {
        EquipmentAttributeDataType dataType = definition.getDataType();
        if (isBlankForType(dataType, request)) {
            if (definition.isRequired()) {
                throw RestException.badRequest("Attribute is required: " + definition.getKey());
            }
            return;
        }
        if (dataType == EquipmentAttributeDataType.NUMBER || dataType == EquipmentAttributeDataType.RANGE) {
            Double value = request.valueNumber();
            if (value == null) {
                throw RestException.badRequest("Attribute requires valueNumber: " + definition.getKey());
            }
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

    private void validateOption(EquipmentAttributeDefinition definition, String value) {
        List<String> options = definition.getOptions() == null ? List.of() : definition.getOptions();
        if (!options.isEmpty() && !options.contains(value)) {
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
}

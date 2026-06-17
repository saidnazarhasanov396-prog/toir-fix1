package com.toir.service.equipment;

import com.toir.dto.equipmentmanualattribute.BulkEquipmentManualAttributeRequest;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeDto;
import com.toir.dto.equipmentmanualattribute.EquipmentManualAttributeRequest;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentManualAttribute;
import com.toir.exception.RestException;
import com.toir.repository.equipment.EquipmentManualAttributeRepository;
import com.toir.repository.equipment.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EquipmentManualAttributeService {

    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_VALUE_LENGTH = 2000;
    private static final Set<String> RESERVED_ATTRIBUTE_KEYS = Set.of(
            "code",
            "name",
            "status",
            "departmentId",
            "department_id",
            "equipmentTypeId",
            "equipment_type_id",
            "arrivalDate",
            "arrival_date",
            "averageOperatingLifeHours",
            "average_operating_life_hours",
            "averageDailyUsage",
            "average_daily_usage",
            "expectedLifetimeHours",
            "expected_lifetime_hours",
            "lifetimeCounterType",
            "lifetime_counter_type",
            "lifetimeMeterId",
            "lifetime_meter_id",
            "lifetimeLimitValue",
            "lifetime_limit_value",
            "lifetimeBaselineValue",
            "lifetime_baseline_value",
            "lifetimeWarningPercent",
            "lifetime_warning_percent",
            "inventoryNumber",
            "inventory_number",
            "plateNumber",
            "plate_number",
            "vin",
            "brand",
            "model",
            "vehicleType",
            "vehicle_type"
    ).stream().map(EquipmentManualAttributeService::normalizeReservedKey).collect(Collectors.toUnmodifiableSet());

    private final EquipmentRepository equipmentRepository;
    private final EquipmentManualAttributeRepository repository;

    @Transactional(readOnly = true)
    public List<EquipmentManualAttributeDto> list(UUID equipmentId) {
        ensureEquipmentExists(equipmentId);
        return repository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .stream()
                .map(EquipmentManualAttributeDto::from)
                .toList();
    }

    @Transactional
    public EquipmentManualAttributeDto create(UUID equipmentId, EquipmentManualAttributeRequest request) {
        assertWriteEnabled();
        ensureEquipmentExists(equipmentId);
        NormalizedAttribute normalized = normalizeAndValidate(request);
        repository.findByEquipmentIdAndKeyIgnoreCaseAndIsDeletedFalse(equipmentId, normalized.key())
                .ifPresent(existing -> {
                    throw RestException.conflict("Manual attribute already exists: " + normalized.key());
                });

        EquipmentManualAttribute attribute = new EquipmentManualAttribute();
        attribute.setEquipmentId(equipmentId);
        attribute.setKey(normalized.key());
        attribute.setValue(normalized.value());
        return EquipmentManualAttributeDto.from(repository.save(attribute));
    }

    @Transactional
    public List<EquipmentManualAttributeDto> replaceAll(UUID equipmentId, BulkEquipmentManualAttributeRequest request) {
        assertWriteEnabled();
        ensureEquipmentExists(equipmentId);
        List<NormalizedAttribute> normalizedAttributes = normalizeBulk(request);
        Map<String, NormalizedAttribute> requestedByKey = new LinkedHashMap<>();
        for (NormalizedAttribute attribute : normalizedAttributes) {
            if (requestedByKey.put(attribute.key(), attribute) != null) {
                throw RestException.badRequest("Duplicate manual attribute key: " + attribute.key());
            }
        }

        Map<String, EquipmentManualAttribute> existingByKey = repository.findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .stream()
                .collect(Collectors.toMap(
                        attribute -> normalizeKey(attribute.getKey()),
                        attribute -> attribute,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<EquipmentManualAttribute> toSave = new ArrayList<>();
        for (EquipmentManualAttribute existing : existingByKey.values()) {
            NormalizedAttribute requested = requestedByKey.get(normalizeKey(existing.getKey()));
            if (requested == null) {
                existing.setDeleted(true);
                toSave.add(existing);
                continue;
            }
            existing.setKey(requested.key());
            existing.setValue(requested.value());
            toSave.add(existing);
        }

        for (NormalizedAttribute requested : requestedByKey.values()) {
            if (existingByKey.containsKey(requested.key())) {
                continue;
            }
            EquipmentManualAttribute attribute = new EquipmentManualAttribute();
            attribute.setEquipmentId(equipmentId);
            attribute.setKey(requested.key());
            attribute.setValue(requested.value());
            toSave.add(attribute);
        }

        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }
        return list(equipmentId);
    }

    @Transactional
    public EquipmentManualAttributeDto update(UUID attributeId, EquipmentManualAttributeRequest request) {
        assertWriteEnabled();
        EquipmentManualAttribute attribute = findActiveAttribute(attributeId);
        ensureEquipmentExists(attribute.getEquipmentId());
        NormalizedAttribute normalized = normalizeAndValidate(request);
        repository.findByEquipmentIdAndKeyIgnoreCaseAndIsDeletedFalse(attribute.getEquipmentId(), normalized.key())
                .filter(existing -> !Objects.equals(existing.getId(), attributeId))
                .ifPresent(existing -> {
                    throw RestException.conflict("Manual attribute already exists: " + normalized.key());
                });
        attribute.setKey(normalized.key());
        attribute.setValue(normalized.value());
        return EquipmentManualAttributeDto.from(repository.save(attribute));
    }

    @Transactional
    public void delete(UUID attributeId) {
        assertWriteEnabled();
        EquipmentManualAttribute attribute = findActiveAttribute(attributeId);
        ensureEquipmentExists(attribute.getEquipmentId());
        attribute.setDeleted(true);
        repository.save(attribute);
    }

    @Transactional(readOnly = true)
    public EquipmentManualAttribute findActiveAttribute(UUID attributeId) {
        return repository.findByIdAndIsDeletedFalse(attributeId)
                .orElseThrow(() -> RestException.notFound("Manual attribute not found: " + attributeId));
    }

    public void assertWriteEnabled() {
    }

    public boolean isWriteEnabled() {
        return true;
    }

    private Equipment ensureEquipmentExists(UUID equipmentId) {
        if (equipmentId == null) {
            throw RestException.badRequest("equipmentId is required");
        }
        return equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
    }

    private List<NormalizedAttribute> normalizeBulk(BulkEquipmentManualAttributeRequest request) {
        if (request == null || request.attributes() == null) {
            return List.of();
        }
        return request.attributes().stream()
                .map(this::normalizeAndValidate)
                .toList();
    }

    private NormalizedAttribute normalizeAndValidate(EquipmentManualAttributeRequest request) {
        if (request == null) {
            throw RestException.badRequest("Manual attribute request is required");
        }
        String key = normalizeKey(request.key());
        String value = normalizeValue(request.value());
        if (key.isBlank()) {
            throw RestException.badRequest("Attribute key cannot be empty");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw RestException.badRequest("Attribute key must not exceed 100 characters");
        }
        if (value.isBlank()) {
            throw RestException.badRequest("Attribute value cannot be empty");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw RestException.badRequest("Attribute value must not exceed 2000 characters");
        }
        if (RESERVED_ATTRIBUTE_KEYS.contains(normalizeReservedKey(key))) {
            throw RestException.badRequest("Manual attribute key is reserved: " + request.key());
        }
        return new NormalizedAttribute(key, value);
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeReservedKey(String key) {
        return String.valueOf(key)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[_\\-\\s]+", "");
    }

    private record NormalizedAttribute(String key, String value) {
    }
}

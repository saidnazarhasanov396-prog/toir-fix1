package com.toir.service.equipment;
import com.toir.entity.equipment.EquipmentType;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;

import com.toir.exception.RestException;
import com.toir.dto.equipmenttype.EquipmentTypeDto;
import com.toir.dto.equipmenttype.EquipmentTypeRequest;
import com.toir.repository.equipment.EquipmentTypeRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class EquipmentTypeService {

    private final EquipmentTypeRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<EquipmentTypeDto> findAll(String search, String category) {
        search = search == null ? null : "%" + search.toLowerCase() + "%";
        return repository.findAllByIsDeletedFalseAndBySearchParam(search, category)
                .stream()
                .map(EquipmentTypeDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EquipmentTypeDto findById(UUID id) {
        return EquipmentTypeDto.from(getOrThrow(id));
    }

    public EquipmentType getEntityOrThrow(UUID id) {
        return getOrThrow(id);
    }

    public EquipmentTypeDto create(EquipmentTypeRequest request) {
        EquipmentType entity = new EquipmentType();
        entity.setCode(nextCode());
        apply(entity, request);
        EquipmentType saved = repository.save(entity);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return EquipmentTypeDto.from(saved);
    }

    public EquipmentTypeDto update(UUID id, EquipmentTypeRequest request) {
        EquipmentType entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        apply(entity, request);
        audit(AuditAction.UPDATE, entity.getId(), oldJson, entity);
        return EquipmentTypeDto.from(entity);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        EquipmentType saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private EquipmentType getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment type not found: " + id));
    }

    private void apply(EquipmentType entity, EquipmentTypeRequest request) {
        entity.setName(request.name());
        entity.setCategory(request.category());
        entity.setDescription(request.description());
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "ET-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("ET", year, sequence);
        while (repository.existsByCode(code)) {
            sequence++;
            code = formatCode("ET", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void audit(AuditAction action, UUID id, String oldJson, EquipmentType current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "equipment_type",
                id != null ? id.toString() : null,
                action,
                AuditModule.EQUIPMENT_TYPE,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Тип оборудования создан";
            case UPDATE -> "Тип оборудования обновлен";
            case DELETE -> "Тип оборудования удален";
            default -> "Действие выполнено над типом оборудования";
        };
    }
}

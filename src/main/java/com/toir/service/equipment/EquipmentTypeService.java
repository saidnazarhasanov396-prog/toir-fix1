package com.toir.service.equipment;
import com.toir.dto.equipmenttype.EquipmentTypeStatsResponse;
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
    public EquipmentTypeStatsResponse getStats(String search, String category) {
        search = search == null ? null : "%" + search.toLowerCase() + "%";
        var stats = repository.getEquipmentTypeStats(category, search);
        return new EquipmentTypeStatsResponse(
                stats.getTotalTypes() == null ? 0 : stats.getTotalTypes(),
                stats.getActiveCategories() == null ? 0 : stats.getActiveCategories(),
                stats.getWithActiveEquipment() == null ? 0 : stats.getWithActiveEquipment(),
                stats.getRecentlyAdded() == null ? 0 : stats.getRecentlyAdded()
        );
    }

    @Transactional(readOnly = true)
    public EquipmentTypeDto findById(UUID id) {
        return EquipmentTypeDto.from(getOrThrow(id));
    }


    @Transactional
    public EquipmentTypeDto create(EquipmentTypeRequest request) {
        EquipmentType entity = new EquipmentType();
        entity.setCode(nextCode());
        apply(entity, request);
        EquipmentType saved = repository.save(entity);

        auditBuilderService.log(
                "equipment_type",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.EQUIPMENT_TYPE,
                "Тип оборудования создан",
                null,
                saved
        );
        return EquipmentTypeDto.from(saved);
    }

    @Transactional
    public EquipmentTypeDto update(UUID id, EquipmentTypeRequest request) {
        EquipmentType entity = getOrThrow(id);
        apply(entity, request);

        EquipmentType save = repository.save(entity);

        auditBuilderService.log(
                "equipment_type",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.EQUIPMENT_TYPE,
                "Тип оборудования обновлен",
                entity,
                save
        );


        return EquipmentTypeDto.from(entity);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        EquipmentType saved = repository.save(entity);

        auditBuilderService.log(
                "equipment_type",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.EQUIPMENT_TYPE,
                "Тип оборудования удален",
                entity,
                null
        );
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
}

package com.toir.service;

import com.toir.dto.spareparttype.SparePartTypeDto;
import com.toir.dto.spareparttype.SparePartTypeRequest;
import com.toir.entity.SparePartType;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.SparePartRepository;
import com.toir.repository.SparePartTypeRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SparePartTypeService {

    private final SparePartTypeRepository repository;
    private final SparePartRepository sparePartRepository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<SparePartTypeDto> findAll(String search, boolean includeInactive) {
        String searchPattern = searchPattern(search);
        List<SparePartType> types = includeInactive
                ? repository.findAllIncludingInactive(searchPattern)
                : repository.findAllActive(searchPattern);
        return types.stream().map(SparePartTypeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public SparePartTypeDto findById(UUID id) {
        return SparePartTypeDto.from(getOrThrow(id));
    }

    @Transactional
    public SparePartTypeDto create(SparePartTypeRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw RestException.badRequest("Spare part type code already exists: " + code);
        }
        SparePartType entity = new SparePartType();
        apply(entity, request, code);
        SparePartType saved = repository.save(entity);
        auditBuilderService.log(
                "spare_part_type",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.SPARE_PART,
                "Тип запасной части создан",
                null,
                saved
        );
        return SparePartTypeDto.from(saved);
    }

    @Transactional
    public SparePartTypeDto update(UUID id, SparePartTypeRequest request) {
        SparePartType entity = getOrThrow(id);
        String code = normalizeCode(request.code());
        if (repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw RestException.badRequest("Spare part type code already exists: " + code);
        }
        apply(entity, request, code);
        SparePartType saved = repository.save(entity);
        auditBuilderService.log(
                "spare_part_type",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.SPARE_PART,
                "Тип запасной части обновлен",
                entity,
                saved
        );
        return SparePartTypeDto.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        SparePartType entity = getOrThrow(id);
        if (sparePartRepository.existsByTypeIdAndIsDeletedFalse(id)) {
            entity.setActive(false);
            repository.save(entity);
        } else {
            repository.delete(entity);
        }
        auditBuilderService.log(
                "spare_part_type",
                id.toString(),
                AuditAction.DELETE,
                AuditModule.SPARE_PART,
                "Тип запасной части удален",
                entity,
                null
        );
    }

    private SparePartType getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Spare part type not found: " + id));
    }

    private void apply(SparePartType entity, SparePartTypeRequest request, String code) {
        entity.setCode(code);
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setDefaultUnit(normalizeOptionalToken(request.defaultUnit()));
        entity.setActive(request.active() == null || request.active());
    }

    private String normalizeCode(String value) {
        String token = trimToNull(value);
        if (token == null) {
            throw RestException.badRequest("Spare part type code is required");
        }
        return token.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalToken(String value) {
        String token = trimToNull(value);
        return token == null ? null : token.toUpperCase(Locale.ROOT);
    }

    private String searchPattern(String search) {
        String token = trimToNull(search);
        return token == null ? null : "%" + token.toLowerCase(Locale.ROOT) + "%";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

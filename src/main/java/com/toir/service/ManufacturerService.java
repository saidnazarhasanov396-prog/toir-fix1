package com.toir.service;
import com.toir.entity.Manufacturer;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.ManufacturerRepository;

import com.toir.exception.RestException;
import com.toir.dto.manufacturer.ManufacturerDto;
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
public class ManufacturerService {

    private final ManufacturerRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<ManufacturerDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(ManufacturerDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ManufacturerDto findById(UUID id) {
        return ManufacturerDto.from(getOrThrow(id));
    }

    public ManufacturerDto create(ManufacturerDto request) {
        Manufacturer entity = new Manufacturer();
        entity.setCode(nextCode());
        apply(entity, request);
        Manufacturer saved = repository.save(entity);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return ManufacturerDto.from(saved);
    }

    public ManufacturerDto update(UUID id, ManufacturerDto request) {
        Manufacturer entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        apply(entity, request);
        audit(AuditAction.UPDATE, entity.getId(), oldJson, entity);
        return ManufacturerDto.from(entity);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        Manufacturer saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null);
    }

    private Manufacturer getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Manufacturer not found: " + id));
    }

    private void apply(Manufacturer entity, ManufacturerDto r) {
        entity.setName(r.name());
        entity.setCountry(r.country());
        entity.setWebsite(r.website());
        entity.setContactInfo(r.contactInfo());
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "MFR-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("MFR", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("MFR", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void audit(AuditAction action, UUID id, String oldJson, Manufacturer current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "manufacturer",
                id != null ? id.toString() : null,
                action,
                AuditModule.MANUFACTURER,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Производитель создан";
            case UPDATE -> "Производитель обновлен";
            case DELETE -> "Производитель удален";
            default -> "Действие выполнено над производителем";
        };
    }
}

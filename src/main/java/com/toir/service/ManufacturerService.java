package com.toir.service;

import com.toir.dto.manufacturer.ManufacturerDto;
import com.toir.entity.Manufacturer;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.ManufacturerRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ManufacturerService {

    private final ManufacturerRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<ManufacturerDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(ManufacturerDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ManufacturerDto findById(UUID id) {
        return ManufacturerDto.from(getOrThrow(id));
    }

    @Transactional
    public ManufacturerDto create(ManufacturerDto request) {
        Manufacturer entity = new Manufacturer();
        entity.setCode(nextCode());
        apply(entity, request);
        Manufacturer saved = repository.save(entity);

        auditBuilderService.log(
                "manufacturer",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.MANUFACTURER,
                "Производитель создан",
                null,
                saved
        );

        return ManufacturerDto.from(saved);
    }

    @Transactional
    public ManufacturerDto update(UUID id, ManufacturerDto request) {
        Manufacturer entity = getOrThrow(id);
        apply(entity, request);

        Manufacturer saved = repository.save(entity);

        auditBuilderService.log(
                "manufacturer",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.MANUFACTURER,
                "Производитель обновлен",
                entity,
                saved
        );

        return ManufacturerDto.from(entity);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        Manufacturer saved = repository.save(entity);

        auditBuilderService.log(
                "manufacturer",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.MANUFACTURER,
                "Производитель удален",
                saved,
                null

        );

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

}

package com.toir.service;
import com.toir.entity.Manufacturer;
import com.toir.repository.ManufacturerRepository;

import com.toir.exception.RestException;
import com.toir.dto.manufacturer.ManufacturerDto;
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

    @Transactional(readOnly = true)
    public List<ManufacturerDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(ManufacturerDto::from).toList();
    }

    @Transactional(readOnly = true)
    public ManufacturerDto findById(UUID id) {
        return ManufacturerDto.from(getOrThrow(id));
    }

    public ManufacturerDto create(ManufacturerDto request) {
        Manufacturer entity = new Manufacturer();
        entity.setCode(nextCode());
        apply(entity, request);
        return ManufacturerDto.from(repository.save(entity));
    }

    public ManufacturerDto update(UUID id, ManufacturerDto request) {
        Manufacturer entity = getOrThrow(id);
        apply(entity, request);
        return ManufacturerDto.from(entity);
    }

    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity);
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

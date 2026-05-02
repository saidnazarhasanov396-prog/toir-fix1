package com.toir.service;
import com.toir.entity.UnitOfMeasurement;
import com.toir.repository.UnitOfMeasurementRepository;

import com.toir.exception.RestException;
import com.toir.dto.uom.UnitOfMeasurementDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class UnitOfMeasurementService {

    private final UnitOfMeasurementRepository repository;


    @Transactional(readOnly = true)
    public List<UnitOfMeasurementDto> findAll(String search) {
        return repository.findAllByIsDeletedFalse(search).stream().map(UnitOfMeasurementDto::from).toList();
    }

    public UnitOfMeasurementDto create(UnitOfMeasurementDto r) {
        UnitOfMeasurement e = new UnitOfMeasurement();
        e.setCode(nextCode());
        e.setName(r.name());
        return UnitOfMeasurementDto.from(repository.save(e));
    }

    public UnitOfMeasurementDto update(UUID id, UnitOfMeasurementDto r) {
        UnitOfMeasurement e = getOrThrow(id);
        e.setName(r.name());
        return UnitOfMeasurementDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private UnitOfMeasurement getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("UoM not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "UOM-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("UOM", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("UOM", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }
}

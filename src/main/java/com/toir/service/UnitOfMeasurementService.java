package com.toir.service;
import com.toir.entity.UnitOfMeasurement;
import com.toir.repository.UnitOfMeasurementRepository;

import com.toir.exception.RestException;
import com.toir.dto.uom.UnitOfMeasurementDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitOfMeasurementService {

    private final UnitOfMeasurementRepository repository;


    @Transactional(readOnly = true)
    public List<UnitOfMeasurementDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(UnitOfMeasurementDto::from).toList();
    }

    public UnitOfMeasurementDto create(UnitOfMeasurementDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("UoM code already exists: " + r.code());
        }
        UnitOfMeasurement e = new UnitOfMeasurement();
        e.setCode(r.code()); e.setName(r.name());
        return UnitOfMeasurementDto.from(repository.save(e));
    }

    public UnitOfMeasurementDto update(UUID id, UnitOfMeasurementDto r) {
        UnitOfMeasurement e = getOrThrow(id);
        e.setCode(r.code()); e.setName(r.name());
        return UnitOfMeasurementDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private UnitOfMeasurement getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("UoM not found: " + id));
    }
}

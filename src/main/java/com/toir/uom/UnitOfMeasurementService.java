package com.toir.uom;

import com.toir.common.exception.RestException;
import com.toir.uom.dto.UnitOfMeasurementDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UnitOfMeasurementService {

    private final UnitOfMeasurementRepository repository;

    public UnitOfMeasurementService(UnitOfMeasurementRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<UnitOfMeasurementDto> findAll() {
        return repository.findAll().stream().map(UnitOfMeasurementDto::from).toList();
    }

    public UnitOfMeasurementDto create(UnitOfMeasurementDto r) {
        if (repository.existsByCode(r.code())) {
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

    public void delete(UUID id) { repository.delete(getOrThrow(id)); }

    private UnitOfMeasurement getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("UoM not found: " + id));
    }
}

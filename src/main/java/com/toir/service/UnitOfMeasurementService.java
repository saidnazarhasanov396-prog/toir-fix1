package com.toir.service;

import com.toir.dto.uom.UnitOfMeasurementDto;
import com.toir.entity.UnitOfMeasurement;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.UnitOfMeasurementRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitOfMeasurementService {

    private final UnitOfMeasurementRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<UnitOfMeasurementDto> findAll(String search) {
        return repository.findAllByIsDeletedFalse(search).stream().map(UnitOfMeasurementDto::from).toList();
    }

    @Transactional
    public UnitOfMeasurementDto create(UnitOfMeasurementDto r) {
        UnitOfMeasurement e = new UnitOfMeasurement();
        e.setCode(nextCode());
        e.setName(r.name());
        UnitOfMeasurement saved = repository.save(e);

        auditBuilderService.log(
                "unit_of_measurement",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.UNIT_OF_MEASUREMENT,
                "Единица измерения создана",
                null,
                saved
        );

        return UnitOfMeasurementDto.from(saved);
    }

    @Transactional
    public UnitOfMeasurementDto update(UUID id, UnitOfMeasurementDto r) {
        UnitOfMeasurement e = getOrThrow(id);
        e.setName(r.name());

        UnitOfMeasurement saved = repository.save(e);

        auditBuilderService.log(
                "unit_of_measurement",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.UNIT_OF_MEASUREMENT,
                "Единица измерения обновлена",
                e,
                saved
        );

        return UnitOfMeasurementDto.from(e);
    }

    @Transactional
    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        UnitOfMeasurement saved = repository.save(entity);

        auditBuilderService.log(
                "unit_of_measurement",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.UNIT_OF_MEASUREMENT,
                "Единица измерения удалена",
                saved,
                null
        );
    }

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

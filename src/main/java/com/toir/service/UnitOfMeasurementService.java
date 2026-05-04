package com.toir.service;
import com.toir.entity.UnitOfMeasurement;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.UnitOfMeasurementRepository;

import com.toir.exception.RestException;
import com.toir.dto.uom.UnitOfMeasurementDto;
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
public class UnitOfMeasurementService {

    private final UnitOfMeasurementRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<UnitOfMeasurementDto> findAll(String search) {
        return repository.findAllByIsDeletedFalse(search).stream().map(UnitOfMeasurementDto::from).toList();
    }

    public UnitOfMeasurementDto create(UnitOfMeasurementDto r) {
        UnitOfMeasurement e = new UnitOfMeasurement();
        e.setCode(nextCode());
        e.setName(r.name());
        UnitOfMeasurement saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return UnitOfMeasurementDto.from(saved);
    }

    public UnitOfMeasurementDto update(UUID id, UnitOfMeasurementDto r) {
        UnitOfMeasurement e = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        e.setName(r.name());
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return UnitOfMeasurementDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        UnitOfMeasurement saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

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

    private void audit(AuditAction action, UUID id, String oldJson, UnitOfMeasurement current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "unit_of_measurement",
                id != null ? id.toString() : null,
                action,
                AuditModule.UNIT_OF_MEASUREMENT,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Единица измерения создана";
            case UPDATE -> "Единица измерения обновлена";
            case DELETE -> "Единица измерения удалена";
            default -> "Действие выполнено над единицей измерения";
        };
    }
}

package com.toir.service;

import com.toir.dto.uom.UnitOfMeasurementDto;
import com.toir.entity.UnitOfMeasurement;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.UnitOfMeasurementRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Optional;
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

    @Transactional(readOnly = true)
    public String normalizeRequiredUnitOrThrow(String rawUnit, String fieldName) {
        String normalizedInput = normalizeInput(rawUnit);
        if (normalizedInput == null) {
            throw RestException.badRequest(fieldName + " is required");
        }
        return normalizeKnownUnitOrThrow(normalizedInput, fieldName);
    }

    @Transactional(readOnly = true)
    public String normalizeOptionalUnitOrNull(String rawUnit) {
        String normalizedInput = normalizeInput(rawUnit);
        if (normalizedInput == null) {
            return null;
        }
        return normalizeKnownUnitOrThrow(normalizedInput, "unit");
    }

    @Transactional
    public UnitOfMeasurementDto create(UnitOfMeasurementDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
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
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
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

    private String normalizeKnownUnitOrThrow(String token, String fieldName) {
        Optional<UnitOfMeasurement> match = findKnownUnit(token);
        if (match.isEmpty()) {
            throw RestException.badRequest(
                    "Unknown " + fieldName + ": " + token + ". Use dictionary values from /api/v1/units-of-measurement"
            );
        }
        return match.get().getName();
    }

    private Optional<UnitOfMeasurement> findKnownUnit(String token) {
        Optional<UUID> unitId = parseUuid(token);
        if (unitId.isPresent()) {
            return repository.findByIdAndIsDeletedFalse(unitId.get());
        }
        return repository.findByTokenIgnoreCase(token).stream().findFirst();
    }

    private Optional<UUID> parseUuid(String token) {
        try {
            return Optional.of(UUID.fromString(token));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private String normalizeInput(String rawUnit) {
        if (rawUnit == null) {
            return null;
        }
        String token = rawUnit.trim();
        return token.isEmpty() ? null : token;
    }
}

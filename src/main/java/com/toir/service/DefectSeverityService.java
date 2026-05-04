package com.toir.service;
import com.toir.entity.DefectSeverity;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.DefectSeverityRepository;

import com.toir.exception.RestException;
import com.toir.dto.defectseverity.DefectSeverityDto;
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
public class DefectSeverityService {

    private final DefectSeverityRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<DefectSeverityDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(DefectSeverityDto::from).toList();
    }

    public DefectSeverityDto create(DefectSeverityDto r) {
        DefectSeverity e = new DefectSeverity();
        e.setCode(nextCode());
        e.setName(r.name()); e.setWeight(r.weight());
        DefectSeverity saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return DefectSeverityDto.from(saved);
    }

    public DefectSeverityDto update(UUID id, DefectSeverityDto r) {
        DefectSeverity e = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        e.setName(r.name()); e.setWeight(r.weight());
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return DefectSeverityDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        DefectSeverity saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

    private DefectSeverity getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Severity not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "DS-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("DS", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("DS", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void audit(AuditAction action, UUID id, String oldJson, DefectSeverity current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "defect_severity",
                id != null ? id.toString() : null,
                action,
                AuditModule.DEFECT_SEVERITY,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Серьезность дефекта создана";
            case UPDATE -> "Серьезность дефекта обновлена";
            case DELETE -> "Серьезность дефекта удалена";
            default -> "Действие выполнено над серьезностью дефекта";
        };
    }
}

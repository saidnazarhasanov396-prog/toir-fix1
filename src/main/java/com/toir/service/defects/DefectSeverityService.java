package com.toir.service.defects;
import com.toir.entity.defects.DefectSeverity;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.defects.DefectSeverityRepository;

import com.toir.exception.RestException;
import com.toir.dto.defectseverity.DefectSeverityDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefectSeverityService {

    private final DefectSeverityRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<DefectSeverityDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(DefectSeverityDto::from).toList();
    }

    @Transactional
    public DefectSeverityDto create(DefectSeverityDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        DefectSeverity e = new DefectSeverity();
        e.setCode(nextCode());
        e.setName(r.name()); e.setWeight(r.weight());
        DefectSeverity saved = repository.save(e);
        auditBuilderService.log(
                "defect_severity",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.DEFECT_SEVERITY,
                "Серьезность дефекта создана",
                null,
                saved
        );


        return DefectSeverityDto.from(saved);
    }

    @Transactional
    public DefectSeverityDto update(UUID id, DefectSeverityDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        DefectSeverity e = getOrThrow(id);
        e.setName(r.name()); e.setWeight(r.weight());
        DefectSeverity updated = repository.save(e);

        auditBuilderService.log(
                "defect_severity",
                updated.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DEFECT_SEVERITY,
                "Серьезность дефекта обновлена",
                e,
                updated
        );

        return DefectSeverityDto.from(e);
    }

    @Transactional
    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        DefectSeverity deleted = repository.save(entity);

        auditBuilderService.log(
                "defect_severity",
                deleted.getId().toString(),
                AuditAction.DELETE,
                AuditModule.DEFECT_SEVERITY,
                "Серьезность дефекта удалена",
                deleted,
                null
        );

    }

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
}

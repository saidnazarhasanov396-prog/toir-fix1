package com.toir.service.defects;
import com.toir.entity.defects.DefectCategory;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.defects.DefectCategoryRepository;

import com.toir.exception.RestException;
import com.toir.dto.defectcategory.DefectCategoryDto;
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
public class DefectCategoryService {

    private final DefectCategoryRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<DefectCategoryDto> findAll(String search) {
        return repository.findAll(search).stream().map(DefectCategoryDto::from).toList();
    }

    public DefectCategoryDto create(DefectCategoryDto r) {
        DefectCategory e = new DefectCategory();
        e.setCode(nextCode());
        e.setName(r.name());
        e.setDescription(r.description());
        DefectCategory saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return DefectCategoryDto.from(saved);
    }

    public DefectCategoryDto update(UUID id, DefectCategoryDto r) {
        DefectCategory e = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        e.setName(r.name());
        e.setDescription(r.description());
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return DefectCategoryDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        DefectCategory saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

    private DefectCategory getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Defect category not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "DC-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("DC", year, sequence);
        while (repository.existsByCode(code)) {
            sequence++;
            code = formatCode("DC", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void audit(AuditAction action, UUID id, String oldJson, DefectCategory current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "defect_category",
                id != null ? id.toString() : null,
                action,
                AuditModule.DEFECT_CATEGORY,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Категория дефекта создана";
            case UPDATE -> "Категория дефекта обновлена";
            case DELETE -> "Категория дефекта удалена";
            default -> "Действие выполнено над категорией дефекта";
        };
    }
}

package com.toir.service.defects;

import com.toir.dto.defectcategory.DefectCategoryDto;
import com.toir.entity.defects.DefectCategory;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.defects.DefectCategoryRepository;
import com.toir.util.AuditBuilderService;
import com.toir.util.CodeGenerationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefectCategoryService {

    private final DefectCategoryRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<DefectCategoryDto> findAll(String search) {
        return repository.findAll(search).stream().map(DefectCategoryDto::from).toList();
    }

    @Transactional
    public DefectCategoryDto create(DefectCategoryDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        DefectCategory e = new DefectCategory();
        e.setCode(nextCode());
        e.setName(r.name());
        e.setDescription(r.description());
        DefectCategory saved = repository.save(e);


        auditBuilderService.log(
                "defect_category",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.DEFECT_CATEGORY,
                "Категория дефекта создана",
                null,
                saved
        );

        return DefectCategoryDto.from(saved);
    }

    @Transactional
    public DefectCategoryDto update(UUID id, DefectCategoryDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        DefectCategory e = getOrThrow(id);
        e.setName(r.name());
        e.setDescription(r.description());
        DefectCategory save = repository.save(e);


        auditBuilderService.log(
                "defect_category",
                save.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.DEFECT_CATEGORY,
                "Категория дефекта обновлена",
                e,
                save
        );

        return DefectCategoryDto.from(e);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        DefectCategory saved = repository.save(entity);

        auditBuilderService.log(
                "defect_category",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.DEFECT_CATEGORY,
                "Категория дефекта удалена",
                entity,
                null
        );

    }

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

}

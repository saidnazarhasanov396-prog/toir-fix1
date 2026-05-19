package com.toir.service;

import com.toir.dto.costcategory.CostCategoryDto;
import com.toir.entity.projects.CostCategory;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CostCategoryService {

    private final CostCategoryRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public List<CostCategoryDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(CostCategoryDto::from).toList();
    }

    @Transactional
    public CostCategoryDto create(CostCategoryDto r) {
        CostCategory e = new CostCategory();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        CostCategory saved = repository.save(e);

        auditBuilderService.log(
                "cost_category",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.COST_CATEGORY,
                "Категория затрат создана",
                null,
                saved
        );

        return CostCategoryDto.from(saved);
    }

    @Transactional
    public CostCategoryDto update(UUID id, CostCategoryDto r) {
        CostCategory e = getOrThrow(id);
        e.setName(r.name()); e.setDescription(r.description());

        CostCategory saved = repository.save(e);

        auditBuilderService.log(
                "cost_category",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.COST_CATEGORY,
                "Категория затрат обновлена",
                e,
                saved
        );

        return CostCategoryDto.from(e);
    }

    @Transactional
    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        CostCategory saved = repository.save(entity);

        auditBuilderService.log(
                "cost_category",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.COST_CATEGORY,
                "Категория затрат удалена",
                saved,
                null
        );
    }

    CostCategory getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Cost category not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "COST-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("COST", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("COST", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }


}

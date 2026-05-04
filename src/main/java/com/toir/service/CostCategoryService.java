package com.toir.service;
import com.toir.entity.CostCategory;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.CostCategoryRepository;

import com.toir.exception.RestException;
import com.toir.dto.costcategory.CostCategoryDto;
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
public class CostCategoryService {

    private final CostCategoryRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<CostCategoryDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(CostCategoryDto::from).toList();
    }

    public CostCategoryDto create(CostCategoryDto r) {
        CostCategory e = new CostCategory();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        CostCategory saved = repository.save(e);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return CostCategoryDto.from(saved);
    }

    public CostCategoryDto update(UUID id, CostCategoryDto r) {
        CostCategory e = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(e);
        e.setName(r.name()); e.setDescription(r.description());
        audit(AuditAction.UPDATE, e.getId(), oldJson, e);
        return CostCategoryDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        CostCategory saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

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

    private void audit(AuditAction action, UUID id, String oldJson, CostCategory current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "cost_category",
                id != null ? id.toString() : null,
                action,
                AuditModule.COST_CATEGORY,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Категория затрат создана";
            case UPDATE -> "Категория затрат обновлена";
            case DELETE -> "Категория затрат удалена";
            default -> "Действие выполнено над категорией затрат";
        };
    }
}

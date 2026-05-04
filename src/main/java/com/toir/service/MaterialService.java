package com.toir.service;
import com.toir.entity.Material;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.MaterialRepository;

import com.toir.exception.RestException;
import com.toir.dto.material.MaterialDto;
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
public class MaterialService {

    private final MaterialRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public List<MaterialDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(MaterialDto::from).toList();
    }

    public MaterialDto create(MaterialDto r) {
        Material m = new Material();
        m.setCode(nextCode());
        apply(m, r);
        Material saved = repository.save(m);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return MaterialDto.from(saved);
    }

    public MaterialDto update(UUID id, MaterialDto r) {
        Material m = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(m);
        apply(m, r);
        audit(AuditAction.UPDATE, m.getId(), oldJson, m);
        return MaterialDto.from(m);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(entity);
        entity.setDeleted(true);
        Material saved = repository.save(entity);
        audit(AuditAction.DELETE, saved.getId(), oldJson, null); }

    private Material getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Material not found: " + id));
    }

    private void apply(Material m, MaterialDto r) {
        m.setName(r.name());
        if (r.kind() != null) m.setKind(r.kind());
        m.setUnit(r.unit());
        m.setSpecification(r.specification());
        m.setMinStock(r.minStock());
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "MAT-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("MAT", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("MAT", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }

    private void audit(AuditAction action, UUID id, String oldJson, Material current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "material",
                id != null ? id.toString() : null,
                action,
                AuditModule.MATERIAL,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Материал создан";
            case UPDATE -> "Материал обновлен";
            case DELETE -> "Материал удален";
            default -> "Действие выполнено над материалом";
        };
    }
}

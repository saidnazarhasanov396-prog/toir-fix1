package com.toir.service;

import com.toir.dto.material.MaterialDto;
import com.toir.entity.Material;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.MaterialRepository;
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
public class MaterialService {

    private final MaterialRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public List<MaterialDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(MaterialDto::from).toList();
    }

    @Transactional
    public MaterialDto create(MaterialDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        Material m = new Material();
        m.setCode(nextCode());
        apply(m, r);
        Material saved = repository.save(m);

        auditBuilderService.log(
                "material",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.MATERIAL,
                "Материал создан",
                null,
                saved
        );

        return MaterialDto.from(saved);
    }

    @Transactional
    public MaterialDto update(UUID id, MaterialDto r) {
        CodeGenerationUtils.rejectClientProvidedCode(r.code());
        Material m = getOrThrow(id);
        apply(m, r);

        Material saved = repository.save(m);

        auditBuilderService.log(
                "material",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.MATERIAL,
                "Материал обновлен",
                m,
                saved
        );

        return MaterialDto.from(m);
    }

    @Transactional
    public void delete(UUID id) {
        var entity = getOrThrow(id);
        entity.setDeleted(true);
        Material saved = repository.save(entity);

        auditBuilderService.log(
                "material",
                saved.getId().toString(),
                AuditAction.DELETE,
                AuditModule.MATERIAL,
                "Материал удален",
                saved,
                null
        );

    }

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

}

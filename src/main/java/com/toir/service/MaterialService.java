package com.toir.service;
import com.toir.entity.Material;
import com.toir.repository.MaterialRepository;

import com.toir.exception.RestException;
import com.toir.dto.material.MaterialDto;
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


    @Transactional(readOnly = true)
    public List<MaterialDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(MaterialDto::from).toList();
    }

    public MaterialDto create(MaterialDto r) {
        Material m = new Material();
        m.setCode(nextCode());
        apply(m, r);
        return MaterialDto.from(repository.save(m));
    }

    public MaterialDto update(UUID id, MaterialDto r) {
        Material m = getOrThrow(id);
        apply(m, r);
        return MaterialDto.from(m);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

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

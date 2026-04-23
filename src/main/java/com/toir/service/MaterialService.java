package com.toir.service;
import com.toir.entity.Material;
import com.toir.repository.MaterialRepository;

import com.toir.exception.RestException;
import com.toir.dto.material.MaterialDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MaterialService {

    private final MaterialRepository repository;

    public MaterialService(MaterialRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<MaterialDto> findAll() {
        return repository.findAll().stream().map(MaterialDto::from).toList();
    }

    public MaterialDto create(MaterialDto r) {
        if (repository.existsByCode(r.code())) {
            throw RestException.conflict("Material code already exists: " + r.code());
        }
        Material m = new Material();
        apply(m, r);
        return MaterialDto.from(repository.save(m));
    }

    public MaterialDto update(UUID id, MaterialDto r) {
        Material m = getOrThrow(id);
        apply(m, r);
        return MaterialDto.from(m);
    }

    public void delete(UUID id) { repository.delete(getOrThrow(id)); }

    private Material getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Material not found: " + id));
    }

    private void apply(Material m, MaterialDto r) {
        m.setCode(r.code());
        m.setName(r.name());
        if (r.kind() != null) m.setKind(r.kind());
        m.setUnit(r.unit());
        m.setSpecification(r.specification());
        m.setMinStock(r.minStock());
    }
}

package com.toir.service;
import com.toir.entity.Defect;
import com.toir.entity.DefectCategory;
import com.toir.repository.DefectCategoryRepository;

import com.toir.exception.RestException;
import com.toir.dto.defectcategory.DefectCategoryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DefectCategoryService {

    private final DefectCategoryRepository repository;

    public DefectCategoryService(DefectCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DefectCategoryDto> findAll() {
        return repository.findAll().stream().map(DefectCategoryDto::from).toList();
    }

    public DefectCategoryDto create(DefectCategoryDto r) {
        if (repository.existsByCode(r.code())) {
            throw RestException.conflict("Defect category code already exists: " + r.code());
        }
        DefectCategory e = new DefectCategory();
        e.setCode(r.code()); e.setName(r.name()); e.setDescription(r.description());
        return DefectCategoryDto.from(repository.save(e));
    }

    public DefectCategoryDto update(UUID id, DefectCategoryDto r) {
        DefectCategory e = getOrThrow(id);
        e.setCode(r.code()); e.setName(r.name()); e.setDescription(r.description());
        return DefectCategoryDto.from(e);
    }

    public void delete(UUID id) { repository.delete(getOrThrow(id)); }

    private DefectCategory getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Defect category not found: " + id));
    }
}

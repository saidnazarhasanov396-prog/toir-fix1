package com.toir.service;
import com.toir.entity.Defect;
import com.toir.entity.DefectCategory;
import com.toir.repository.DefectCategoryRepository;

import com.toir.exception.RestException;
import com.toir.dto.defectcategory.DefectCategoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefectCategoryService {

    private final DefectCategoryRepository repository;


    @Transactional(readOnly = true)
    public List<DefectCategoryDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(DefectCategoryDto::from).toList();
    }

    public DefectCategoryDto create(DefectCategoryDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
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

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private DefectCategory getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Defect category not found: " + id));
    }
}

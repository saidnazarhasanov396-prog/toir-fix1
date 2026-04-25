package com.toir.service;
import com.toir.entity.CostCategory;
import com.toir.repository.CostCategoryRepository;

import com.toir.exception.RestException;
import com.toir.dto.costcategory.CostCategoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CostCategoryService {

    private final CostCategoryRepository repository;


    @Transactional(readOnly = true)
    public List<CostCategoryDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(CostCategoryDto::from).toList();
    }

    public CostCategoryDto create(CostCategoryDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Cost category code already exists: " + r.code());
        }
        CostCategory e = new CostCategory();
        e.setCode(r.code()); e.setName(r.name()); e.setDescription(r.description());
        return CostCategoryDto.from(repository.save(e));
    }

    public CostCategoryDto update(UUID id, CostCategoryDto r) {
        CostCategory e = getOrThrow(id);
        e.setCode(r.code()); e.setName(r.name()); e.setDescription(r.description());
        return CostCategoryDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    CostCategory getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Cost category not found: " + id));
    }
}

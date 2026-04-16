package com.toir.costcategory;

import com.toir.common.exception.RestException;
import com.toir.costcategory.dto.CostCategoryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CostCategoryService {

    private final CostCategoryRepository repository;

    public CostCategoryService(CostCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CostCategoryDto> findAll() {
        return repository.findAll().stream().map(CostCategoryDto::from).toList();
    }

    public CostCategoryDto create(CostCategoryDto r) {
        if (repository.existsByCode(r.code())) {
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

    public void delete(UUID id) { repository.delete(getOrThrow(id)); }

    CostCategory getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Cost category not found: " + id));
    }
}

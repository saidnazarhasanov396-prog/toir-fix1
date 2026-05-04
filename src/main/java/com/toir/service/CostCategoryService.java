package com.toir.service;
import com.toir.entity.projects.CostCategory;
import com.toir.repository.CostCategoryRepository;

import com.toir.exception.RestException;
import com.toir.dto.costcategory.CostCategoryDto;
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


    @Transactional(readOnly = true)
    public List<CostCategoryDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(CostCategoryDto::from).toList();
    }

    public CostCategoryDto create(CostCategoryDto r) {
        CostCategory e = new CostCategory();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        return CostCategoryDto.from(repository.save(e));
    }

    public CostCategoryDto update(UUID id, CostCategoryDto r) {
        CostCategory e = getOrThrow(id);
        e.setName(r.name()); e.setDescription(r.description());
        return CostCategoryDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

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

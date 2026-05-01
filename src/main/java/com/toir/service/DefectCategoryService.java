package com.toir.service;
import com.toir.entity.DefectCategory;
import com.toir.repository.DefectCategoryRepository;

import com.toir.exception.RestException;
import com.toir.dto.defectcategory.DefectCategoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DefectCategoryService {

    private final DefectCategoryRepository repository;


    @Transactional(readOnly = true)
    public List<DefectCategoryDto> findAll(String search) {
        return repository.findAll(search).stream().map(DefectCategoryDto::from).toList();
    }

    public DefectCategoryDto create(DefectCategoryDto r) {
        DefectCategory e = new DefectCategory();
        e.setCode(nextCode());
        e.setName(r.name());
        e.setDescription(r.description());
        return DefectCategoryDto.from(repository.save(e));
    }

    public DefectCategoryDto update(UUID id, DefectCategoryDto r) {
        DefectCategory e = getOrThrow(id);
        e.setName(r.name());
        e.setDescription(r.description());
        return DefectCategoryDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private DefectCategory getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Defect category not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "DC-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("DC", year, sequence);
        while (repository.existsByCode(code)) {
            sequence++;
            code = formatCode("DC", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }
}

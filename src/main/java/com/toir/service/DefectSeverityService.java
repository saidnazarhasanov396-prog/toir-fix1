package com.toir.service;
import com.toir.entity.DefectSeverity;
import com.toir.repository.DefectSeverityRepository;

import com.toir.exception.RestException;
import com.toir.dto.defectseverity.DefectSeverityDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DefectSeverityService {

    private final DefectSeverityRepository repository;


    @Transactional(readOnly = true)
    public List<DefectSeverityDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(DefectSeverityDto::from).toList();
    }

    public DefectSeverityDto create(DefectSeverityDto r) {
        DefectSeverity e = new DefectSeverity();
        e.setCode(nextCode());
        e.setName(r.name()); e.setWeight(r.weight());
        return DefectSeverityDto.from(repository.save(e));
    }

    public DefectSeverityDto update(UUID id, DefectSeverityDto r) {
        DefectSeverity e = getOrThrow(id);
        e.setName(r.name()); e.setWeight(r.weight());
        return DefectSeverityDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private DefectSeverity getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Severity not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "DS-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("DS", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("DS", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }
}

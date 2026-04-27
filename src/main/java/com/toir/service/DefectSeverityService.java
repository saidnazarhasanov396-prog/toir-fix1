package com.toir.service;
import com.toir.entity.DefectSeverity;
import com.toir.repository.DefectSeverityRepository;

import com.toir.exception.RestException;
import com.toir.dto.defectseverity.DefectSeverityDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class DefectSeverityService {

    private final DefectSeverityRepository repository;


    @Transactional(readOnly = true)
    public List<DefectSeverityDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(DefectSeverityDto::from).toList();
    }

    public DefectSeverityDto create(DefectSeverityDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Severity code already exists: " + r.code());
        }
        DefectSeverity e = new DefectSeverity();
        e.setCode(r.code()); e.setName(r.name()); e.setWeight(r.weight());
        return DefectSeverityDto.from(repository.save(e));
    }

    public DefectSeverityDto update(UUID id, DefectSeverityDto r) {
        DefectSeverity e = getOrThrow(id);
        e.setCode(r.code()); e.setName(r.name()); e.setWeight(r.weight());
        return DefectSeverityDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private DefectSeverity getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Severity not found: " + id));
    }
}

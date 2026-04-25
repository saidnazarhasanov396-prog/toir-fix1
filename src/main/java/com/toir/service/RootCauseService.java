package com.toir.service;
import com.toir.entity.RootCause;
import com.toir.repository.RootCauseRepository;

import com.toir.exception.RestException;
import com.toir.dto.rootcause.RootCauseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RootCauseService {

    private final RootCauseRepository repository;


    @Transactional(readOnly = true)
    public List<RootCauseDto> findAll() {
        return repository.findAllByIsDeletedFalse().stream().map(RootCauseDto::from).toList();
    }

    public RootCauseDto create(RootCauseDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Root cause code already exists: " + r.code());
        }
        RootCause e = new RootCause();
        e.setCode(r.code()); e.setName(r.name()); e.setDescription(r.description());
        return RootCauseDto.from(repository.save(e));
    }

    public RootCauseDto update(UUID id, RootCauseDto r) {
        RootCause e = getOrThrow(id);
        e.setCode(r.code()); e.setName(r.name()); e.setDescription(r.description());
        return RootCauseDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private RootCause getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Root cause not found: " + id));
    }
}

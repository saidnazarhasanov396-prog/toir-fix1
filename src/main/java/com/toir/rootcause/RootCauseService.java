package com.toir.rootcause;

import com.toir.common.exception.RestException;
import com.toir.rootcause.dto.RootCauseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RootCauseService {

    private final RootCauseRepository repository;

    public RootCauseService(RootCauseRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RootCauseDto> findAll() {
        return repository.findAll().stream().map(RootCauseDto::from).toList();
    }

    public RootCauseDto create(RootCauseDto r) {
        if (repository.existsByCode(r.code())) {
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

    public void delete(UUID id) { repository.delete(getOrThrow(id)); }

    private RootCause getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Root cause not found: " + id));
    }
}

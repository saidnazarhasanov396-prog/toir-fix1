package com.toir.service;
import com.toir.entity.RootCause;
import com.toir.repository.RootCauseRepository;

import com.toir.exception.RestException;
import com.toir.dto.rootcause.RootCauseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class RootCauseService {

    private final RootCauseRepository repository;


    @Transactional(readOnly = true)
    public List<RootCauseDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(RootCauseDto::from).toList();
    }

    public RootCauseDto create(RootCauseDto r) {
        RootCause e = new RootCause();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        return RootCauseDto.from(repository.save(e));
    }

    public RootCauseDto update(UUID id, RootCauseDto r) {
        RootCause e = getOrThrow(id);
        e.setName(r.name()); e.setDescription(r.description());
        return RootCauseDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private RootCause getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Root cause not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "RC-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("RC", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("RC", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }
}

package com.toir.service;
import com.toir.entity.FailureReason;
import com.toir.repository.FailureReasonRepository;

import com.toir.exception.RestException;
import com.toir.dto.failurereason.FailureReasonDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class FailureReasonService {

    private final FailureReasonRepository repository;


    @Transactional(readOnly = true)
    public List<FailureReasonDto> findAll(String search) {
        return repository.findAllBySearch(search).stream().map(FailureReasonDto::from).toList();
    }

    public FailureReasonDto create(FailureReasonDto r) {
        FailureReason e = new FailureReason();
        e.setCode(nextCode());
        e.setName(r.name()); e.setDescription(r.description());
        return FailureReasonDto.from(repository.save(e));
    }

    public FailureReasonDto update(UUID id, FailureReasonDto r) {
        FailureReason e = getOrThrow(id);
        e.setName(r.name()); e.setDescription(r.description());
        return FailureReasonDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private FailureReason getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Failure reason not found: " + id));
    }

    private String nextCode() {
        int year = Year.now().getValue();
        String codePrefix = "FR-" + year + "-";
        long sequence = repository.maxSequenceByCodePrefix(codePrefix) + 1;
        String code = formatCode("FR", year, sequence);
        while (repository.existsByCodeAndIsDeletedFalse(code)) {
            sequence++;
            code = formatCode("FR", year, sequence);
        }
        return code;
    }

    private String formatCode(String prefix, int year, long sequence) {
        return "%s-%d-%04d".formatted(prefix, year, sequence);
    }
}

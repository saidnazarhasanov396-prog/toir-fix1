package com.toir.service;
import com.toir.entity.FailureReason;
import com.toir.repository.FailureReasonRepository;

import com.toir.exception.RestException;
import com.toir.dto.failurereason.FailureReasonDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class FailureReasonService {

    private final FailureReasonRepository repository;


    @Transactional(readOnly = true)
    public List<FailureReasonDto> findAll() {
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream().map(FailureReasonDto::from).toList();
    }

    public FailureReasonDto create(FailureReasonDto r) {
        if (repository.existsByCodeAndIsDeletedFalse(r.code())) {
            throw RestException.conflict("Failure reason code already exists: " + r.code());
        }
        FailureReason e = new FailureReason();
        e.setCode(r.code()); e.setName(r.name()); e.setDescription(r.description());
        return FailureReasonDto.from(repository.save(e));
    }

    public FailureReasonDto update(UUID id, FailureReasonDto r) {
        FailureReason e = getOrThrow(id);
        e.setCode(r.code()); e.setName(r.name()); e.setDescription(r.description());
        return FailureReasonDto.from(e);
    }

    public void delete(UUID id) { var entity = getOrThrow(id);
        entity.setDeleted(true);
        repository.save(entity); }

    private FailureReason getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Failure reason not found: " + id));
    }
}

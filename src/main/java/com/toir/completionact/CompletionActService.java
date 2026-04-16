package com.toir.completionact;

import com.toir.common.exception.RestException;
import com.toir.completionact.dto.CompletionActDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class CompletionActService {

    private final CompletionActRepository repository;

    public CompletionActService(CompletionActRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CompletionActDto findByWorkOrder(UUID workOrderId) {
        return CompletionActDto.from(repository.findByWorkOrderId(workOrderId)
                .orElseThrow(() -> RestException.notFound("Completion act not found for WO: " + workOrderId)));
    }

    public CompletionActDto create(UUID workOrderId, CompletionActDto r) {
        if (repository.findByWorkOrderId(workOrderId).isPresent()) {
            throw RestException.conflict("Completion act already exists for this work order");
        }
        if (repository.existsByActNumber(r.actNumber())) {
            throw RestException.conflict("Act number already exists: " + r.actNumber());
        }
        CompletionAct a = new CompletionAct();
        a.setWorkOrderId(workOrderId);
        a.setActNumber(r.actNumber());
        a.setSummary(r.summary());
        return CompletionActDto.from(repository.save(a));
    }

    public CompletionActDto sign(UUID id, UUID signerId) {
        CompletionAct a = repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Completion act not found: " + id));
        a.setSignedById(signerId);
        a.setSignedAt(Instant.now());
        return CompletionActDto.from(a);
    }
}

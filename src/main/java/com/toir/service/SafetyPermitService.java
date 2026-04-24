package com.toir.service;
import com.toir.entity.SafetyPermit;
import com.toir.repository.SafetyPermitRepository;
import com.toir.enums.SafetyPermitStatus;

import com.toir.exception.RestException;
import com.toir.dto.safetypermit.SafetyPermitDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class SafetyPermitService {

    private final SafetyPermitRepository repository;

    public SafetyPermitService(SafetyPermitRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SafetyPermitDto findByWorkOrder(UUID workOrderId) {
        return SafetyPermitDto.from(repository.findByWorkOrderId(workOrderId)
                .orElseThrow(() -> RestException.notFound("Safety permit not found for WO: " + workOrderId)));
    }

    public SafetyPermitDto create(UUID workOrderId, SafetyPermitDto r) {
        if (repository.findByWorkOrderId(workOrderId).isPresent()) {
            throw RestException.conflict("Safety permit already exists for this work order");
        }
        if (repository.existsByPermitNumber(r.permitNumber())) {
            throw RestException.conflict("Permit number already exists: " + r.permitNumber());
        }
        SafetyPermit p = new SafetyPermit();
        p.setWorkOrderId(workOrderId);
        p.setPermitNumber(r.permitNumber());
        p.setIssuedById(r.issuedById());
        p.setValidUntil(r.validUntil());
        p.setNotes(r.notes());
        return SafetyPermitDto.from(repository.save(p));
    }

    public SafetyPermitDto issue(UUID id) {
        SafetyPermit p = getOrThrow(id);
        p.setStatus(SafetyPermitStatus.ISSUED);
        p.setIssuedAt(Instant.now());
        return SafetyPermitDto.from(p);
    }

    public SafetyPermitDto close(UUID id) {
        SafetyPermit p = getOrThrow(id);
        p.setStatus(SafetyPermitStatus.CLOSED);
        return SafetyPermitDto.from(p);
    }

    private SafetyPermit getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Safety permit not found: " + id));
    }
}

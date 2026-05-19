package com.toir.service;

import com.toir.dto.safetypermit.SafetyPermitDto;
import com.toir.entity.SafetyPermit;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.SafetyPermitStatus;
import com.toir.exception.RestException;
import com.toir.repository.SafetyPermitRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SafetyPermitService {

    private final SafetyPermitRepository repository;
    private final AuditBuilderService auditBuilderService;

    @Transactional(readOnly = true)
    public SafetyPermitDto findByWorkOrder(UUID workOrderId) {
        return SafetyPermitDto.from(repository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Safety permit not found for WO: " + workOrderId)));
    }

    @Transactional
    public SafetyPermitDto create(UUID workOrderId, SafetyPermitDto r) {
        if (repository.findByWorkOrderIdAndIsDeletedFalse(workOrderId).isPresent()) {
            throw RestException.conflict("Safety permit already exists for this work order");
        }
        if (repository.existsByPermitNumberAndIsDeletedFalse(r.permitNumber())) {
            throw RestException.conflict("Permit number already exists: " + r.permitNumber());
        }
        SafetyPermit p = new SafetyPermit();
        p.setWorkOrderId(workOrderId);
        p.setPermitNumber(r.permitNumber());
        p.setIssuedById(r.issuedById());
        p.setValidUntil(r.validUntil());
        p.setNotes(r.notes());
        SafetyPermit saved = repository.save(p);

        auditBuilderService.log(
                "safety_permit",
               saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.SAFETY_PERMIT,
                "Наряд-допуск создан",
                 null,
                saved
        );

        return SafetyPermitDto.from(saved);
    }

    @Transactional
    public SafetyPermitDto issue(UUID id) {
        SafetyPermit p = getOrThrow(id);
        p.setStatus(SafetyPermitStatus.ISSUED);
        p.setIssuedAt(Instant.now());

        SafetyPermit saved = repository.save(p);

        auditBuilderService.log(
                "safety_permit",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.SAFETY_PERMIT,
                "Наряд-допуск обновлен",
                p,
                saved
        );

        return SafetyPermitDto.from(p);
    }

    @Transactional
    public SafetyPermitDto close(UUID id) {
        SafetyPermit p = getOrThrow(id);
        p.setStatus(SafetyPermitStatus.CLOSED);

        SafetyPermit saved = repository.save(p);
        auditBuilderService.log(
                "safety_permit",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.SAFETY_PERMIT,
                "Наряд-допуск обновлен",
                p,
                saved
        );

        return SafetyPermitDto.from(p);
    }

    private SafetyPermit getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Safety permit not found: " + id));
    }
}

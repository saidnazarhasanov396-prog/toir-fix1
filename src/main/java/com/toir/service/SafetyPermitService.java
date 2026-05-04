package com.toir.service;
import com.toir.entity.SafetyPermit;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.SafetyPermitRepository;
import com.toir.enums.SafetyPermitStatus;

import com.toir.exception.RestException;
import com.toir.dto.safetypermit.SafetyPermitDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class SafetyPermitService {

    private final SafetyPermitRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public SafetyPermitDto findByWorkOrder(UUID workOrderId) {
        return SafetyPermitDto.from(repository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Safety permit not found for WO: " + workOrderId)));
    }

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
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return SafetyPermitDto.from(saved);
    }

    public SafetyPermitDto issue(UUID id) {
        SafetyPermit p = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(p);
        p.setStatus(SafetyPermitStatus.ISSUED);
        p.setIssuedAt(Instant.now());
        audit(AuditAction.UPDATE, p.getId(), oldJson, p);
        return SafetyPermitDto.from(p);
    }

    public SafetyPermitDto close(UUID id) {
        SafetyPermit p = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(p);
        p.setStatus(SafetyPermitStatus.CLOSED);
        audit(AuditAction.UPDATE, p.getId(), oldJson, p);
        return SafetyPermitDto.from(p);
    }

    private SafetyPermit getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Safety permit not found: " + id));
    }

    private void audit(AuditAction action, UUID id, String oldJson, SafetyPermit current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "safety_permit",
                id != null ? id.toString() : null,
                action,
                AuditModule.SAFETY_PERMIT,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Наряд-допуск создан";
            case UPDATE -> "Наряд-допуск обновлен";
            case DELETE -> "Наряд-допуск удален";
            default -> "Действие выполнено над нарядом-допуском";
        };
    }
}

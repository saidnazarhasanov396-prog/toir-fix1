package com.toir.service;
import com.toir.entity.CompletionAct;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.repository.CompletionActRepository;

import com.toir.exception.RestException;
import com.toir.dto.completionact.CompletionActDto;
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
public class CompletionActService {

    private final CompletionActRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;


    @Transactional(readOnly = true)
    public CompletionActDto findByWorkOrder(UUID workOrderId) {
        return CompletionActDto.from(repository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Completion act not found for WO: " + workOrderId)));
    }

    public CompletionActDto create(UUID workOrderId, CompletionActDto r) {
        if (repository.findByWorkOrderIdAndIsDeletedFalse(workOrderId).isPresent()) {
            throw RestException.conflict("Completion act already exists for this work order");
        }
        if (repository.existsByActNumberAndIsDeletedFalse(r.actNumber())) {
            throw RestException.conflict("Act number already exists: " + r.actNumber());
        }
        CompletionAct a = new CompletionAct();
        a.setWorkOrderId(workOrderId);
        a.setActNumber(r.actNumber());
        a.setSummary(r.summary());
        CompletionAct saved = repository.save(a);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return CompletionActDto.from(saved);
    }

    public CompletionActDto sign(UUID id, UUID signerId) {
        CompletionAct a = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Completion act not found: " + id));
        String oldJson = auditSerializationService.toJson(a);
        a.setSignedById(signerId);
        a.setSignedAt(Instant.now());
        audit(AuditAction.UPDATE, a.getId(), oldJson, a);
        return CompletionActDto.from(a);
    }

    private void audit(AuditAction action, UUID id, String oldJson, CompletionAct current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "completion_act",
                id != null ? id.toString() : null,
                action,
                AuditModule.COMPLETION_ACT,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Акт завершения создан";
            case UPDATE -> "Акт завершения обновлен";
            case DELETE -> "Акт завершения удален";
            default -> "Действие выполнено над актом завершения";
        };
    }
}

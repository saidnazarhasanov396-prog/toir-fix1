package com.toir.service;

import com.toir.dto.completionact.CompletionActDto;
import com.toir.entity.CompletionAct;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.CompletionActRepository;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompletionActService {

    private final CompletionActRepository repository;
    private final AuditBuilderService auditBuilderService;


    @Transactional(readOnly = true)
    public CompletionActDto findByWorkOrder(UUID workOrderId) {
        return CompletionActDto.from(repository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Completion act not found for WO: " + workOrderId)));
    }

    @Transactional
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

        auditBuilderService.log(
                "completion_act",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.COMPLETION_ACT,
                "Акт завершения создан",
                null,
                saved
        );

        return CompletionActDto.from(saved);
    }

    @Transactional
    public CompletionActDto sign(UUID id, UUID signerId) {
        CompletionAct a = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Completion act not found: " + id));
        a.setSignedById(signerId);
        a.setSignedAt(Instant.now());

        CompletionAct saved = repository.save(a);

        auditBuilderService.log(
                "completion_act",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.COMPLETION_ACT,
                "Акт завершения обновлен",
                a,
                saved
        );

        return CompletionActDto.from(a);
    }

}

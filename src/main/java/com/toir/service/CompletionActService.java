package com.toir.service;

import com.toir.dto.completionact.CompletionActDto;
import com.toir.entity.CompletionAct;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.RepairAcceptanceStage;
import com.toir.enums.RepairAcceptanceStatus;
import com.toir.exception.RestException;
import com.toir.repository.CompletionActRepository;
import com.toir.repository.maintenance.RepairAcceptanceRepository;
import com.toir.security.ScopeAccessService;
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
    private final RepairAcceptanceRepository repairAcceptanceRepository;
    private final AuditBuilderService auditBuilderService;
    private final ScopeAccessService scopeAccessService;


    @Transactional(readOnly = true)
    public CompletionActDto findByWorkOrder(UUID workOrderId) {
        return CompletionActDto.from(repository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Completion act not found for WO: " + workOrderId)));
    }

    @Transactional
    public CompletionActDto create(UUID workOrderId, CompletionActDto r) {
        assertFinalAcceptanceAccepted(workOrderId);
        validateLinkedAcceptance(workOrderId, r.repairAcceptanceId());
        if (repository.findByWorkOrderIdAndIsDeletedFalse(workOrderId).isPresent()) {
            throw RestException.conflict("Completion act already exists for this work order");
        }
        if (repository.existsByActNumberAndIsDeletedFalse(r.actNumber())) {
            throw RestException.conflict("Act number already exists: " + r.actNumber());
        }
        CompletionAct a = new CompletionAct();
        a.setWorkOrderId(workOrderId);
        a.setRepairAcceptanceId(r.repairAcceptanceId());
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
    public CompletionActDto ensureForAcceptedFinal(UUID workOrderId, UUID repairAcceptanceId, String summary) {
        assertFinalAcceptanceAccepted(workOrderId);
        return repository.findByWorkOrderIdAndIsDeletedFalse(workOrderId)
                .map(CompletionActDto::from)
                .orElseGet(() -> {
                    CompletionAct act = new CompletionAct();
                    act.setWorkOrderId(workOrderId);
                    act.setRepairAcceptanceId(repairAcceptanceId);
                    act.setActNumber("ACT-" + workOrderId);
                    act.setSummary(summary);
                    CompletionAct saved = repository.save(act);
                    auditBuilderService.log(
                            "completion_act",
                            saved.getId().toString(),
                            AuditAction.CREATE,
                            AuditModule.COMPLETION_ACT,
                            "Акт завершения автоматически создан после приёмки",
                            null,
                            saved);
                    return CompletionActDto.from(saved);
                });
    }

    @Transactional
    public CompletionActDto sign(UUID id) {
        CompletionAct a = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Completion act not found: " + id));
        UUID signerId = scopeAccessService.currentUserIdOrNull();
        if (signerId == null) {
            throw RestException.conflict("Authenticated signer could not be resolved");
        }
        if (a.getSignedById() != null) {
            if (signerId.equals(a.getSignedById())) {
                return CompletionActDto.from(a);
            }
            throw RestException.conflict("Completion act is already signed by another user");
        }
        assertFinalAcceptanceAccepted(a.getWorkOrderId());
        validateLinkedAcceptance(a.getWorkOrderId(), a.getRepairAcceptanceId());
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

        return CompletionActDto.from(saved);
    }

    private void assertFinalAcceptanceAccepted(UUID workOrderId) {
        if (!repairAcceptanceRepository.existsAcceptedFinalByWorkOrderId(workOrderId)) {
            throw RestException.badRequest("Final repair acceptance must be ACCEPTED before completion act can be created or signed");
        }
    }

    private void validateLinkedAcceptance(UUID workOrderId, UUID repairAcceptanceId) {
        if (repairAcceptanceId == null) {
            return;
        }
        repairAcceptanceRepository.findByIdAndIsDeletedFalse(repairAcceptanceId)
                .filter(acceptance -> workOrderId.equals(acceptance.getWorkOrderId()))
                .filter(acceptance -> acceptance.getStage() == RepairAcceptanceStage.FINAL)
                .filter(acceptance -> acceptance.getStatus() == RepairAcceptanceStatus.ACCEPTED)
                .orElseThrow(() -> RestException.badRequest("Linked repair acceptance must be FINAL and ACCEPTED for this work order"));
    }

}

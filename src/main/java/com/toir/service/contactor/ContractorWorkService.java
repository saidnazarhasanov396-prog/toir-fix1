package com.toir.service.contactor;
import com.toir.entity.contractors.ContractorWork;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ContractorWorkStatus;
import com.toir.repository.contarctor.ContractorWorkRepository;

import com.toir.exception.RestException;
import com.toir.dto.contractorwork.ContractorWorkDto;
import com.toir.util.AuditBuilderService;
import com.toir.util.AuditSerializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ContractorWorkService {

    private final ContractorWorkRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<ContractorWorkDto> findByContractor(UUID contractorId) {
        return repository.findAllByContractorIdAndIsDeletedFalse(contractorId).stream().map(ContractorWorkDto::from).toList();
    }

    public ContractorWorkDto create(ContractorWorkDto r) {
        ContractorWork w = new ContractorWork();
        w.setContractorId(r.contractorId());
        w.setWorkOrderId(r.workOrderId());
        w.setDescription(r.description());
        w.setCreatedById(r.createdById());
        ContractorWork saved = repository.save(w);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return ContractorWorkDto.from(saved);
    }

    public ContractorWorkDto start(UUID id) {
        ContractorWork w = getOrThrow(id);
        String oldJson = auditSerializationService.toJson(w);
        w.setStatus(ContractorWorkStatus.IN_PROGRESS);
        w.setStartedAt(Instant.now());
        audit(AuditAction.UPDATE, w.getId(), oldJson, w);
        return ContractorWorkDto.from(w);
    }

    public ContractorWorkDto complete(UUID id, String result, Double cost) {
        ContractorWork w = getOrThrow(id);
        if (result == null || result.isBlank()) {
            throw RestException.badRequest("Result is required to complete work");
        }
        String oldJson = auditSerializationService.toJson(w);
        w.setStatus(ContractorWorkStatus.COMPLETED);
        w.setCompletedAt(Instant.now());
        w.setResult(result);
        w.setCost(cost);
        audit(AuditAction.UPDATE, w.getId(), oldJson, w);
        return ContractorWorkDto.from(w);
    }

    public ContractorWorkDto accept(UUID id, UUID acceptedById, String comment) {
        ContractorWork w = getOrThrow(id);
        if (w.getStatus() != ContractorWorkStatus.COMPLETED) {
            throw RestException.badRequest("Only completed works can be accepted");
        }
        String oldJson = auditSerializationService.toJson(w);
        w.setStatus(ContractorWorkStatus.ACCEPTED);
        w.setAcceptedById(acceptedById);
        w.setAcceptedAt(Instant.now());
        w.setAcceptanceComment(comment);
        audit(AuditAction.UPDATE, w.getId(), oldJson, w);
        return ContractorWorkDto.from(w);
    }

    private ContractorWork getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Contractor work not found: " + id));
    }

    private void audit(AuditAction action, UUID id, String oldJson, ContractorWork current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "contractor_work",
                id != null ? id.toString() : null,
                action,
                AuditModule.CONTRACTOR_WORK,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Работа подрядчика создана";
            case UPDATE -> "Работа подрядчика обновлена";
            case DELETE -> "Работа подрядчика удалена";
            default -> "Действие выполнено над работой подрядчика";
        };
    }
}

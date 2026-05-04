package com.toir.service;
import com.toir.entity.projects.ActualCost;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.actualCost.ActualCostRepository;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.exception.RestException;
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
public class ActualCostService {

    private final ActualCostRepository repository;
    private final AuditBuilderService auditBuilderService;
    private final AuditSerializationService auditSerializationService;

    @Transactional(readOnly = true)
    public List<ActualCostDto> findPending() {
        return repository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING).stream().map(ActualCostDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ActualCostDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalseOrderByUpdatedAtDesc(workOrderId).stream().map(ActualCostDto::from).toList();
    }

    @Transactional
    public ActualCostDto create(ActualCostDto r) {
        ActualCost c = new ActualCost();
        c.setWorkOrderId(r.workOrderId());
        c.setRepairRequestId(r.repairRequestId());
        c.setContractorWorkId(r.contractorWorkId());
        c.setBudgetLineId(r.budgetLineId());
        c.setCostCategoryId(r.costCategoryId());
        c.setAmount(r.amount());
        c.setNotes(r.notes());
        c.setStatus(ActualCostStatus.PENDING);
        ActualCost saved = repository.save(c);
        audit(AuditAction.CREATE, saved.getId(), null, saved);
        return ActualCostDto.from(saved);
    }

    @Transactional
    public ActualCostDto review(UUID id, boolean approve, UUID reviewerId, String comment) {
        ActualCost c = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Actual cost not found: " + id));
        String oldJson = auditSerializationService.toJson(c);
        c.setStatus(approve ? ActualCostStatus.APPROVED : ActualCostStatus.REJECTED);
        c.setReviewedById(reviewerId);
        c.setReviewedAt(Instant.now());
        c.setReviewComment(comment);
        audit(AuditAction.UPDATE, c.getId(), oldJson, c);
        return ActualCostDto.from(c);
    }

    private void audit(AuditAction action, UUID id, String oldJson, ActualCost current) {
        String newJson = current == null ? null : auditSerializationService.toJson(current);
        auditBuilderService.log(
                "actual_cost",
                id != null ? id.toString() : null,
                action,
                AuditModule.ACTUAL_COST,
                auditMessage(action),
                oldJson,
                newJson
        );
    }

    private String auditMessage(AuditAction action) {
        return switch (action) {
            case CREATE -> "Фактическая стоимость создана";
            case UPDATE -> "Фактическая стоимость обновлена";
            case DELETE -> "Фактическая стоимость удалена";
            default -> "Действие выполнено над фактической стоимостью";
        };
    }
}

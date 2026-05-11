package com.toir.service;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.entity.projects.ActualCost;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.exception.RestException;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.util.AuditBuilderService;
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

        auditBuilderService.log(
                "actual_cost",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.ACTUAL_COST,
                "Фактическая стоимость создана",
                null,
                saved
        );

        return ActualCostDto.from(saved);
    }

    @Transactional
    public ActualCostDto review(UUID id, boolean approve, UUID reviewerId, String comment) {
        if (!approve && (comment == null || comment.isBlank())) {
            throw RestException.badRequest("Rejection comment is required");
        }
        ActualCost c = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Actual cost not found: " + id));
        c.setStatus(approve ? ActualCostStatus.APPROVED : ActualCostStatus.REJECTED);
        c.setReviewedById(reviewerId);
        c.setReviewedAt(Instant.now());
        c.setReviewComment(comment);

        ActualCost saved = repository.save(c);

        auditBuilderService.log(
                "actual_cost",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.ACTUAL_COST,
                "Фактическая стоимость обновлена",
                c,
                saved
        );
        return ActualCostDto.from(c);
    }
}

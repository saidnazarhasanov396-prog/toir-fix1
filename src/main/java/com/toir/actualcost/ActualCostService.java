package com.toir.actualcost;

import com.toir.actualcost.dto.ActualCostDto;
import com.toir.common.exception.RestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ActualCostService {

    private final ActualCostRepository repository;

    public ActualCostService(ActualCostRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ActualCostDto> findPending() {
        return repository.findAllByStatus(ActualCostStatus.PENDING).stream().map(ActualCostDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ActualCostDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderId(workOrderId).stream().map(ActualCostDto::from).toList();
    }

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
        return ActualCostDto.from(repository.save(c));
    }

    public ActualCostDto review(UUID id, boolean approve, UUID reviewerId, String comment) {
        ActualCost c = repository.findById(id)
                .orElseThrow(() -> RestException.notFound("Actual cost not found: " + id));
        c.setStatus(approve ? ActualCostStatus.APPROVED : ActualCostStatus.REJECTED);
        c.setReviewedById(reviewerId);
        c.setReviewedAt(Instant.now());
        c.setReviewComment(comment);
        return ActualCostDto.from(c);
    }
}

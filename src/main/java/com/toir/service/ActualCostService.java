package com.toir.service;
import com.toir.entity.ActualCost;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.ActualCostRepository;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.exception.RestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActualCostService {

    private final ActualCostRepository repository;

    @Transactional(readOnly = true)
    public List<ActualCostDto> findPending() {
        return repository.findAllByStatusAndIsDeletedFalse(ActualCostStatus.PENDING).stream().map(ActualCostDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ActualCostDto> findByWorkOrder(UUID workOrderId) {
        return repository.findAllByWorkOrderIdAndIsDeletedFalse(workOrderId).stream().map(ActualCostDto::from).toList();
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
        return ActualCostDto.from(repository.save(c));
    }

    @Transactional
    public ActualCostDto review(UUID id, boolean approve, UUID reviewerId, String comment) {
        ActualCost c = repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Actual cost not found: " + id));
        c.setStatus(approve ? ActualCostStatus.APPROVED : ActualCostStatus.REJECTED);
        c.setReviewedById(reviewerId);
        c.setReviewedAt(Instant.now());
        c.setReviewComment(comment);
        return ActualCostDto.from(c);
    }
}

package com.toir.service.finance;

import com.toir.entity.projects.BudgetEvent;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.BudgetStatus;
import com.toir.exception.RestException;
import com.toir.repository.projects.BudgetEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BudgetCommitmentService {

    private static final double EPSILON = 0.000001d;

    private final BudgetLineRepository budgetLineRepository;
    private final BudgetEventRepository budgetEventRepository;

    @Transactional
    public void commitBudget(UUID budgetLineId, double amount, String sourceType,
                             UUID sourceId, UUID actorUserId, String comment) {
        BudgetLine line = budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)
                .orElseThrow(() -> RestException.notFound("Budget line not found: " + budgetLineId));

        validateBudgetLineForCommitment(line);
        validateRemainingAmount(line, amount);

        double oldCommitted = line.getCommittedAmount();
        line.setCommittedAmount(oldCommitted + amount);
        budgetLineRepository.save(line);

        recordBudgetEvent(line, "COMMITMENT_ADDED", oldCommitted,
                line.getCommittedAmount(), sourceType, sourceId, actorUserId, comment);
    }

    @Transactional
    public void releaseBudget(UUID budgetLineId, double amount, String sourceType,
                              UUID sourceId, UUID actorUserId, String comment) {
        BudgetLine line = budgetLineRepository.findByIdAndIsDeletedFalse(budgetLineId)
                .orElseThrow(() -> RestException.notFound("Budget line not found: " + budgetLineId));

        double oldCommitted = line.getCommittedAmount();
        double newCommitted = Math.max(0, oldCommitted - amount);
        line.setCommittedAmount(newCommitted);
        budgetLineRepository.save(line);

        recordBudgetEvent(line, "COMMITMENT_REMOVED", oldCommitted, newCommitted,
                sourceType, sourceId, actorUserId, comment);
    }

    private void validateBudgetLineForCommitment(BudgetLine line) {
        MaintenanceBudget budget = line.getBudget();
        if (budget == null) {
            throw RestException.badRequest("Budget line has no budget");
        }
        if (budget.getStatus() != BudgetStatus.APPROVED && budget.getStatus() != BudgetStatus.LOCKED) {
            throw RestException.badRequest("Budget must be APPROVED or LOCKED for commitment");
        }
    }

    private void validateRemainingAmount(BudgetLine line, double amount) {
        double available = line.getAvailableForCommitment();
        if (amount - available > EPSILON) {
            throw RestException.badRequest(
                    "Insufficient budget for commitment: available=" + available + ", requested=" + amount);
        }
    }

    private void recordBudgetEvent(BudgetLine line, String eventType, double oldCommitted,
                                   double newCommitted, String sourceType, UUID sourceId,
                                   UUID actorUserId, String comment) {
        BudgetEvent event = new BudgetEvent();
        event.setBudgetId(line.getBudget().getId());
        event.setBudgetLineId(line.getId());
        event.setEventType(eventType);
        event.setOldValues("{\"committedAmount\":" + oldCommitted + "}");
        event.setNewValues("{\"committedAmount\":" + newCommitted
                + ",\"sourceType\":\"" + sourceType + "\",\"sourceId\":\"" + sourceId + "\"}");
        event.setActorUserId(actorUserId);
        event.setComment(comment);
        event.setOccurredAt(Instant.now());
        budgetEventRepository.save(event);
    }
}

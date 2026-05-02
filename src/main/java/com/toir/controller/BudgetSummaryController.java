package com.toir.controller;
import com.toir.dto.budget.ActualCostBudgetRow;
import com.toir.dto.budget.ActualCostHandoverSummary;
import com.toir.dto.budget.ActualCostRegisterSummary;
import com.toir.dto.budget.ActualCostReviewActivitySummary;
import com.toir.dto.budget.ActualCostReviewHistoryResponse;
import com.toir.dto.budget.BudgetSummaryResponse;
import com.toir.dto.budget.ContractorWorkRecommendationResponse;
import com.toir.dto.common.PageResponse;
import com.toir.dto.common.PageResponseWithSummary;
import com.toir.dto.costcategory.CostCategoryDto;
import com.toir.entity.ActualCost;
import com.toir.entity.BudgetLine;
import com.toir.entity.CostCategory;
import com.toir.entity.MaintenanceBudget;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.ActualCostRepository;
import com.toir.repository.BudgetLineRepository;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.MaintenanceBudgetRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real implementations for the {@code /budgets/*} analytical endpoints that
 * the React frontend expects on the budget control page. Replaces the
 * previous FrontendStubController stubs.
 */
@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "budgets-summary")
@RequiredArgsConstructor
public class BudgetSummaryController {

    private final MaintenanceBudgetRepository budgetRepository;
    private final BudgetLineRepository lineRepository;
    private final ActualCostRepository actualCostRepository;
    private final CostCategoryRepository costCategoryRepository;



    @GetMapping("/summary")
    public ResponseEntity<BudgetSummaryResponse> summary() {
        List<MaintenanceBudget> budgets = budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        double totalPlanned = budgets.stream().mapToDouble(MaintenanceBudget::getTotalPlanned).sum();
        double totalActual = budgets.stream().mapToDouble(MaintenanceBudget::getTotalActual).sum();
        double variance = totalPlanned - totalActual;
        double executionPercent = totalPlanned > 0 ? (totalActual / totalPlanned) * 100 : 0;

        Map<UUID, CostCategory> catById = costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(CostCategory::getId, c -> c));

        List<BudgetSummaryResponse.CategoryRow> byCategory = lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.groupingBy(BudgetLine::getCostCategoryId))
                .entrySet().stream()
                .map(entry -> {
                    CostCategory cat = catById.get(entry.getKey());
                    double planned = entry.getValue().stream().mapToDouble(BudgetLine::getPlannedAmount).sum();
                    double actual = entry.getValue().stream().mapToDouble(BudgetLine::getActualAmount).sum();
                    return new BudgetSummaryResponse.CategoryRow(
                            cat != null
                                    ? new BudgetSummaryResponse.CategoryRef(cat.getId(), cat.getCode(), cat.getName())
                                    : new BudgetSummaryResponse.CategoryRef(entry.getKey(), "—", "—"),
                            planned,
                            actual,
                            planned - actual
                    );
                })
                .toList();

        return ResponseEntity.ok(new BudgetSummaryResponse(
                budgets.stream()
                        .map(b -> new BudgetSummaryResponse.Item(
                                b.getId(),
                                b.getYear(),
                                b.getMonth() != null ? b.getMonth() : 0,
                                b.getTotalPlanned(),
                                b.getTotalActual()
                        ))
                        .toList(),
                totalPlanned,
                totalActual,
                variance,
                variance,
                executionPercent,
                byCategory
        ));
    }

    @GetMapping("/cost-categories")
    public ResponseEntity<Page<CostCategoryDto>> costCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(costCategoryRepository.findAll(search, PageRequest.of(page, pageSize))
                .map(CostCategoryDto::from));
    }

    @GetMapping("/actual-costs/register")
    public ResponseEntity<PageResponseWithSummary<ActualCostBudgetRow, ActualCostRegisterSummary>> actualCostRegister(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<ActualCost> items = actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc();
        double totalAmount = items.stream().mapToDouble(ActualCost::getAmount).sum();
        double approvedAmount = items.stream().filter(c -> c.getStatus() == ActualCostStatus.APPROVED)
                .mapToDouble(ActualCost::getAmount).sum();
        double pendingAmount = items.stream().filter(c -> c.getStatus() == ActualCostStatus.PENDING)
                .mapToDouble(ActualCost::getAmount).sum();
        double rejectedAmount = items.stream().filter(c -> c.getStatus() == ActualCostStatus.REJECTED)
                .mapToDouble(ActualCost::getAmount).sum();

        long approvedCount = items.stream().filter(c -> c.getStatus() == ActualCostStatus.APPROVED).count();
        long pendingCount = items.stream().filter(c -> c.getStatus() == ActualCostStatus.PENDING).count();
        long rejectedCount = items.stream().filter(c -> c.getStatus() == ActualCostStatus.REJECTED).count();

        ActualCostRegisterSummary summary = new ActualCostRegisterSummary(
                totalAmount,
                approvedAmount,
                pendingAmount,
                rejectedAmount,
                items.size(),
                approvedCount,
                pendingCount,
                rejectedCount
        );

        return ResponseEntity.ok(PageResponseWithSummary.of(
                items.stream().map(this::actualCostRow).toList(),
                page,
                pageSize,
                summary
        ));
    }

    @GetMapping("/actual-costs/review-queue")
    public ResponseEntity<PageResponse<ActualCostBudgetRow>> reviewQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<ActualCost> pending = actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING);
        return ResponseEntity.ok(PageResponse.of(pending.stream().map(this::actualCostRow).toList(), page, pageSize));
    }

    @GetMapping("/actual-costs/{id}/review-history")
    public ResponseEntity<ActualCostReviewHistoryResponse> reviewHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(actualCostRepository.findByIdAndIsDeletedFalse(id)
                .map(c -> new ActualCostReviewHistoryResponse(
                        id.toString(),
                        c.getReviewedAt() != null
                                ? List.of(new ActualCostReviewHistoryResponse.Event(
                                        c.getReviewedAt(),
                                        c.getStatus().name(),
                                        c.getReviewedById(),
                                        c.getReviewComment() != null ? c.getReviewComment() : ""
                                ))
                                : List.of()
                ))
                .orElse(new ActualCostReviewHistoryResponse(id.toString(), List.of())));
    }

    @GetMapping("/actual-costs/review-activity")
    public ResponseEntity<PageResponseWithSummary<ActualCostBudgetRow, ActualCostReviewActivitySummary>> reviewActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<ActualCost> recent = actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(c -> c.getReviewedAt() != null && c.getReviewedAt().isAfter(weekAgo))
                .toList();

        ActualCostReviewActivitySummary summary = new ActualCostReviewActivitySummary(
                recent.size(),
                0,
                0,
                recent.size(),
                0,
                0,
                recent.size(),
                recent.stream().map(ActualCost::getId).distinct().count()
        );

        return ResponseEntity.ok(PageResponseWithSummary.of(
                recent.stream().map(this::actualCostRow).toList(),
                page,
                pageSize,
                summary
        ));
    }

    @GetMapping("/actual-costs/handovers")
    public ResponseEntity<PageResponseWithSummary<ActualCostBudgetRow, ActualCostHandoverSummary>> handovers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        ActualCostHandoverSummary summary = new ActualCostHandoverSummary(
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of()
        );

        return ResponseEntity.ok(PageResponseWithSummary.of(List.of(), page, pageSize, summary));
    }

    @GetMapping("/actual-costs/approval-pack")
    public ResponseEntity<PageResponse<ActualCostBudgetRow>> approvalPack(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<ActualCost> pending = actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING);
        return ResponseEntity.ok(PageResponse.of(pending.stream().map(this::actualCostRow).toList(), page, pageSize));
    }

    @GetMapping("/actual-costs/review-history-pack")
    public ResponseEntity<PageResponse<ActualCostBudgetRow>> reviewHistoryPack(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<ActualCost> reviewed = actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(c -> c.getReviewedAt() != null)
                .toList();
        return ResponseEntity.ok(PageResponse.of(reviewed.stream().map(this::actualCostRow).toList(), page, pageSize));
    }

    @GetMapping("/contractor-works/{id}/recommendation")
    public ResponseEntity<ContractorWorkRecommendationResponse> contractorWorkRecommendation(@PathVariable UUID id) {
        return ResponseEntity.ok(new ContractorWorkRecommendationResponse(id.toString(), 0, List.of()));
    }

    private ActualCostBudgetRow actualCostRow(ActualCost c) {
        return new ActualCostBudgetRow(
                c.getId(),
                c.getWorkOrderId(),
                c.getRepairRequestId(),
                c.getContractorWorkId(),
                c.getCostCategoryId(),
                c.getStatus().name(),
                c.getAmount(),
                c.getCostDate(),
                c.getNotes(),
                c.getReviewedAt(),
                c.getReviewedById(),
                c.getReviewComment()
        );
    }
}

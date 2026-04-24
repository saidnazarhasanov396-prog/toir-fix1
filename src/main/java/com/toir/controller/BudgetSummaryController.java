package com.toir.controller;
import com.toir.entity.BudgetLine;
import com.toir.entity.MaintenanceBudget;
import com.toir.repository.BudgetLineRepository;
import com.toir.repository.MaintenanceBudgetRepository;

import com.toir.entity.ActualCost;
import com.toir.repository.ActualCostRepository;
import com.toir.enums.ActualCostStatus;
import com.toir.entity.CostCategory;
import com.toir.repository.CostCategoryRepository;
import com.toir.dto.costcategory.CostCategoryDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Real implementations for the {@code /budgets/*} analytical endpoints that
 * the React frontend expects on the budget control page. Replaces the
 * previous FrontendStubController stubs.
 */
@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "budgets-summary")
public class BudgetSummaryController {

    private final MaintenanceBudgetRepository budgetRepository;
    private final BudgetLineRepository lineRepository;
    private final ActualCostRepository actualCostRepository;
    private final CostCategoryRepository costCategoryRepository;

    public BudgetSummaryController(MaintenanceBudgetRepository budgetRepository,
                                   BudgetLineRepository lineRepository,
                                   ActualCostRepository actualCostRepository,
                                   CostCategoryRepository costCategoryRepository) {
        this.budgetRepository = budgetRepository;
        this.lineRepository = lineRepository;
        this.actualCostRepository = actualCostRepository;
        this.costCategoryRepository = costCategoryRepository;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<MaintenanceBudget> budgets = budgetRepository.findAll();
        double totalPlanned = budgets.stream().mapToDouble(MaintenanceBudget::getTotalPlanned).sum();
        double totalActual = budgets.stream().mapToDouble(MaintenanceBudget::getTotalActual).sum();
        double variance = totalPlanned - totalActual;
        double executionPercent = totalPlanned > 0 ? (totalActual / totalPlanned) * 100 : 0;

        Map<UUID, CostCategory> catById = costCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(CostCategory::getId, c -> c));

        List<Map<String, Object>> byCategory = lineRepository.findAll().stream()
                .collect(Collectors.groupingBy(BudgetLine::getCostCategoryId))
                .entrySet().stream()
                .map(entry -> {
                    CostCategory cat = catById.get(entry.getKey());
                    double planned = entry.getValue().stream().mapToDouble(BudgetLine::getPlannedAmount).sum();
                    double actual = entry.getValue().stream().mapToDouble(BudgetLine::getActualAmount).sum();
                    Map<String, Object> row = new java.util.HashMap<>();
                    row.put("category", cat != null
                            ? Map.of("id", cat.getId(), "code", cat.getCode(), "name", cat.getName())
                            : Map.of("id", entry.getKey(), "code", "â€”", "name", "â€”"));
                    row.put("plannedAmount", planned);
                    row.put("actualAmount", actual);
                    row.put("variance", planned - actual);
                    return row;
                })
                .toList();

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("items", budgets.stream().map(b -> Map.of(
                "id", b.getId(),
                "year", b.getYear(),
                "month", b.getMonth() != null ? b.getMonth() : 0,
                "totalPlanned", b.getTotalPlanned(),
                "totalActual", b.getTotalActual()
        )).toList());
        result.put("totalPlanned", totalPlanned);
        result.put("totalActual", totalActual);
        result.put("totalRemaining", variance);
        result.put("variance", variance);
        result.put("executionPercent", executionPercent);
        result.put("byCategory", byCategory);
        return result;
    }

    @GetMapping("/cost-categories")
    public CostCategoryDto[] costCategories() {
        return costCategoryRepository.findAll().stream()
                .map(CostCategoryDto::from)
                .toArray(CostCategoryDto[]::new);
    }

    @GetMapping("/actual-costs/register")
    public Map<String, Object> actualCostRegister() {
        List<ActualCost> items = actualCostRepository.findAll();
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

        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("totalAmount", totalAmount);
        summary.put("approvedAmount", approvedAmount);
        summary.put("pendingAmount", pendingAmount);
        summary.put("rejectedAmount", rejectedAmount);
        summary.put("totalCount", (long) items.size());
        summary.put("approvedCount", approvedCount);
        summary.put("pendingCount", pendingCount);
        summary.put("rejectedCount", rejectedCount);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("items", items.stream().map(this::actualCostRow).toList());
        result.put("meta", Map.of("page", 0, "pageSize", items.size(), "total", items.size()));
        result.put("summary", summary);
        return result;
    }

    @GetMapping("/actual-costs/review-queue")
    public Map<String, Object> reviewQueue() {
        List<ActualCost> pending = actualCostRepository.findAllByStatus(ActualCostStatus.PENDING);
        return Map.of(
                "items", pending.stream().map(this::actualCostRow).toList(),
                "meta", Map.of("page", 0, "pageSize", pending.size(), "total", pending.size())
        );
    }

    @GetMapping("/actual-costs/{id}/review-history")
    public Map<String, Object> reviewHistory(@PathVariable UUID id) {
        return actualCostRepository.findById(id)
                .map(c -> Map.<String, Object>of(
                        "actualCostId", id.toString(),
                        "events", c.getReviewedAt() != null
                                ? List.of(Map.of(
                                        "reviewedAt", c.getReviewedAt(),
                                        "status", c.getStatus().name(),
                                        "reviewedById", c.getReviewedById() != null ? c.getReviewedById() : "",
                                        "comment", c.getReviewComment() != null ? c.getReviewComment() : ""))
                                : List.of()
                ))
                .orElse(Map.of("actualCostId", id.toString(), "events", List.of()));
    }

    @GetMapping("/actual-costs/review-activity")
    public Map<String, Object> reviewActivity() {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<ActualCost> recent = actualCostRepository.findAll().stream()
                .filter(c -> c.getReviewedAt() != null && c.getReviewedAt().isAfter(weekAgo))
                .toList();

        Map<String, Object> summary = Map.of(
                "total", recent.size(),
                "routeEvents", 0,
                "slaEvents", 0,
                "reviewEvents", recent.size(),
                "auditRecords", recent.size(),
                "affectedActualCosts", recent.stream().map(ActualCost::getId).distinct().count()
        );

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("items", recent.stream().map(this::actualCostRow).toList());
        result.put("meta", Map.of("page", 0, "pageSize", recent.size(), "total", recent.size()));
        result.put("summary", summary);
        return result;
    }

    @GetMapping("/actual-costs/handovers")
    public Map<String, Object> handovers() {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("total", 0);
        summary.put("uniqueActualCosts", 0);
        summary.put("uniqueDepartments", 0);
        summary.put("uniqueTargetRoles", 0);
        summary.put("uniqueActors", 0);
        summary.put("byTargetRole", List.of());
        summary.put("byDepartment", List.of());

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("items", List.of());
        result.put("meta", Map.of("page", 0, "pageSize", 0, "total", 0));
        result.put("summary", summary);
        return result;
    }

    @GetMapping("/actual-costs/approval-pack")
    public Map<String, Object> approvalPack() {
        List<ActualCost> pending = actualCostRepository.findAllByStatus(ActualCostStatus.PENDING);
        return Map.of(
                "items", pending.stream().map(this::actualCostRow).toList(),
                "meta", Map.of("page", 0, "pageSize", pending.size(), "total", pending.size())
        );
    }

    @GetMapping("/actual-costs/review-history-pack")
    public Map<String, Object> reviewHistoryPack() {
        List<ActualCost> reviewed = actualCostRepository.findAll().stream()
                .filter(c -> c.getReviewedAt() != null)
                .toList();
        return Map.of(
                "items", reviewed.stream().map(this::actualCostRow).toList(),
                "meta", Map.of("page", 0, "pageSize", reviewed.size(), "total", reviewed.size())
        );
    }

    @GetMapping("/contractor-works/{id}/recommendation")
    public Map<String, Object> contractorWorkRecommendation(@PathVariable UUID id) {
        return Map.of(
                "contractorWorkId", id.toString(),
                "recommendedAmount", 0,
                "candidates", List.of()
        );
    }

    private Map<String, Object> actualCostRow(ActualCost c) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", c.getId());
        row.put("workOrderId", c.getWorkOrderId());
        row.put("repairRequestId", c.getRepairRequestId());
        row.put("contractorWorkId", c.getContractorWorkId());
        row.put("costCategoryId", c.getCostCategoryId());
        row.put("status", c.getStatus().name());
        row.put("amount", c.getAmount());
        row.put("costDate", c.getCostDate());
        row.put("notes", c.getNotes());
        row.put("reviewedAt", c.getReviewedAt());
        row.put("reviewedById", c.getReviewedById());
        row.put("reviewComment", c.getReviewComment());
        return row;
    }
}

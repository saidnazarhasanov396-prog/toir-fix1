package com.toir.service.maintanance;

import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.entity.Department;
import com.toir.entity.projects.BudgetEvent;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.AuditAction;
import com.toir.enums.AuditModule;
import com.toir.enums.BudgetStatus;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetEventRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.security.ScopeAccessService;
import com.toir.util.AuditBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceBudgetService {

    private final MaintenanceBudgetRepository repository;
    private final BudgetLineRepository lineRepository;
    private final DepartmentRepository departmentRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final AuditBuilderService auditBuilderService;
    private final ScopeAccessService scopeAccessService;
    private final BudgetEventRepository budgetEventRepository;

    @Transactional(readOnly = true)
    public List<MaintenanceBudgetDto> findByYear(int year) {
        if (scopeAccessService.isScopeAdmin()) {
            return repository.findAllByYearAndIsDeletedFalse(year).stream().map(MaintenanceBudgetDto::from).toList();
        }
        UUID departmentId = scopeAccessService.enforceDepartmentScope(null);
        if (departmentId == null) {
            throw forbidden();
        }
        return repository.findAllByDepartmentIdAndYearAndIsDeletedFalse(departmentId, year).stream()
                .map(MaintenanceBudgetDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MaintenanceBudgetDto> findFiltered(Integer year,
                                                   Integer month,
                                                   UUID requestedDepartmentId,
                                                   String sortBy,
                                                   String sortDir) {
        UUID effectiveDepartmentId = requestedDepartmentId;
        if (!scopeAccessService.isScopeAdmin()) {
            effectiveDepartmentId = scopeAccessService.enforceDepartmentScope(requestedDepartmentId);
            if (effectiveDepartmentId == null) {
                throw forbidden();
            }
        }
        Comparator<MaintenanceBudget> comparator = budgetComparator(sortBy);
        if (sortDir == null || sortDir.isBlank() || "desc".equalsIgnoreCase(sortDir)) {
            comparator = comparator.reversed();
        }
        UUID departmentFilter = effectiveDepartmentId;
        return repository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .filter(budget -> year == null || budget.getYear() == year)
                .filter(budget -> month == null || Objects.equals(budget.getMonth(), month))
                .filter(budget -> departmentFilter == null || Objects.equals(budget.getDepartmentId(), departmentFilter))
                .sorted(comparator)
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        filtered -> {
                            Set<UUID> deptIds = filtered.stream()
                                    .map(MaintenanceBudget::getDepartmentId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());
                            Map<UUID, String> deptNames = deptIds.isEmpty() ? Map.of()
                                    : departmentRepository.findAllByIdInAndIsDeletedFalse(deptIds).stream()
                                            .collect(Collectors.toMap(Department::getId, Department::getName));
                            Map<UUID, String> categoryNames = costCategoryNamesById(filtered);
                            return filtered.stream()
                                    .map(b -> toDto(b, deptNames.get(b.getDepartmentId()), categoryNames))
                                    .toList();
                        }
                ));
    }

    @Transactional(readOnly = true)
    public MaintenanceBudgetDto findById(UUID id) {
        MaintenanceBudget budget = getOrThrow(id);
        assertCanAccessBudget(budget);
        String deptName = budget.getDepartmentId() != null
                ? departmentRepository.findByIdAndIsDeletedFalse(budget.getDepartmentId())
                        .map(Department::getName).orElse(null)
                : null;
        return toDto(budget, deptName, costCategoryNamesById(List.of(budget)));
    }

    @Transactional
    public MaintenanceBudgetDto create(MaintenanceBudgetDto r) {
        assertCanCreateBudget(r.departmentId());
        MaintenanceBudget b = new MaintenanceBudget();
        b.setYear(r.year());
        b.setMonth(r.month());
        b.setDepartmentId(r.departmentId());
        MaintenanceBudget saved = repository.save(b);

        auditBuilderService.log(
                "maintenance_budget",
                saved.getId().toString(),
                AuditAction.CREATE,
                AuditModule.MAINTENANCE_BUDGET,
                "Бюджет обслуживания создан",
                null,
                saved
        );

        return MaintenanceBudgetDto.from(saved);
    }

    @Transactional
    @Deprecated(forRemoval = false)
    public MaintenanceBudgetDto approve(UUID id) {
        return approve(id, null, null);
    }

    @Transactional
    public MaintenanceBudgetDto submit(UUID id, UUID actorUserId, String comment) {
        MaintenanceBudget budget = getOrThrow(id);
        assertCanAccessBudget(budget);
        if (budget.getStatus() != BudgetStatus.DRAFT && budget.getStatus() != BudgetStatus.REJECTED) {
            throw RestException.badRequest("Only DRAFT or REJECTED budgets can be submitted");
        }
        return changeStatus(budget, BudgetStatus.SUBMITTED, actorUserId, comment, "STATUS_SUBMITTED");
    }

    @Transactional
    public MaintenanceBudgetDto approve(UUID id, UUID actorUserId, String comment) {
        MaintenanceBudget b = getOrThrow(id);
        assertCanAccessBudget(b);
        if (b.getStatus() != BudgetStatus.SUBMITTED) {
            throw RestException.badRequest("Only SUBMITTED budgets can be approved");
        }
        return changeStatus(b, BudgetStatus.APPROVED, actorUserId, comment, "STATUS_APPROVED");
    }

    @Transactional
    public MaintenanceBudgetDto reject(UUID id, UUID actorUserId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest("Budget rejection comment is required");
        }
        MaintenanceBudget budget = getOrThrow(id);
        assertCanAccessBudget(budget);
        if (budget.getStatus() != BudgetStatus.SUBMITTED) {
            throw RestException.badRequest("Only SUBMITTED budgets can be rejected");
        }
        return changeStatus(budget, BudgetStatus.REJECTED, actorUserId, comment, "STATUS_REJECTED");
    }

    @Transactional
    public MaintenanceBudgetDto lock(UUID id, UUID actorUserId, String comment) {
        MaintenanceBudget budget = getOrThrow(id);
        assertCanAccessBudget(budget);
        if (budget.getStatus() != BudgetStatus.APPROVED) {
            throw RestException.badRequest("Only APPROVED budgets can be locked");
        }
        return changeStatus(budget, BudgetStatus.LOCKED, actorUserId, comment, "STATUS_LOCKED");
    }

    @Transactional
    public MaintenanceBudgetDto close(UUID id, UUID actorUserId, String comment) {
        MaintenanceBudget budget = getOrThrow(id);
        assertCanAccessBudget(budget);
        if (budget.getStatus() != BudgetStatus.APPROVED && budget.getStatus() != BudgetStatus.LOCKED) {
            throw RestException.badRequest("Only APPROVED or LOCKED budgets can be closed");
        }
        return changeStatus(budget, BudgetStatus.CLOSED, actorUserId, comment, "STATUS_CLOSED");
    }

    @Transactional
    public MaintenanceBudgetDto reopen(UUID id, UUID actorUserId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw RestException.badRequest("Budget reopen comment is required");
        }
        MaintenanceBudget budget = getOrThrow(id);
        assertCanAccessBudget(budget);
        if (budget.getStatus() != BudgetStatus.CLOSED) {
            throw RestException.badRequest("Only CLOSED budgets can be reopened");
        }
        return changeStatus(budget, BudgetStatus.LOCKED, actorUserId, comment, "STATUS_REOPENED");
    }

    @Transactional
    public MaintenanceBudgetDto transfer(UUID budgetId,
                                         UUID fromBudgetLineId,
                                         UUID toBudgetLineId,
                                         double amount,
                                         UUID actorUserId,
                                         String comment) {
        if (fromBudgetLineId == null || toBudgetLineId == null || fromBudgetLineId.equals(toBudgetLineId)) {
            throw RestException.badRequest("Transfer requires two different budget lines");
        }
        if (amount <= 0) {
            throw RestException.badRequest("Transfer amount must be positive");
        }
        MaintenanceBudget budget = getOrThrow(budgetId);
        assertCanAccessBudget(budget);
        assertBudgetCanChangePlan(budget);
        BudgetLine from = budgetLineOrThrow(fromBudgetLineId);
        BudgetLine to = budgetLineOrThrow(toBudgetLineId);
        assertLineBelongsToBudget(from, budget);
        assertLineBelongsToBudget(to, budget);

        double newFromAmount = from.getPlannedAmount() - amount;
        assertPlannedAmountNotBelowActual(from, newFromAmount);
        from.setPlannedAmount(newFromAmount);
        to.setPlannedAmount(to.getPlannedAmount() + amount);
        lineRepository.save(from);
        lineRepository.save(to);
        MaintenanceBudget saved = repository.save(budget);
        recordBudgetEvent(
                budget.getId(),
                null,
                "LINE_TRANSFERRED",
                "{\"fromBudgetLineId\":\"" + fromBudgetLineId + "\",\"toBudgetLineId\":\"" + toBudgetLineId
                        + "\",\"amount\":" + amount + "}",
                "{\"fromPlannedAmount\":" + from.getPlannedAmount() + ",\"toPlannedAmount\":"
                        + to.getPlannedAmount() + "}",
                actorUserId,
                comment
        );
        return MaintenanceBudgetDto.from(saved);
    }

    @Transactional
    public MaintenanceBudgetDto reviseLine(UUID budgetId,
                                           UUID budgetLineId,
                                           double plannedAmount,
                                           UUID actorUserId,
                                           String comment) {
        if (plannedAmount <= 0) {
            throw RestException.badRequest("Budget line planned amount must be positive");
        }
        MaintenanceBudget budget = getOrThrow(budgetId);
        assertCanAccessBudget(budget);
        assertBudgetCanChangePlan(budget);
        BudgetLine line = budgetLineOrThrow(budgetLineId);
        assertLineBelongsToBudget(line, budget);
        assertPlannedAmountNotBelowActual(line, plannedAmount);

        double oldLineAmount = line.getPlannedAmount();
        double delta = plannedAmount - oldLineAmount;
        line.setPlannedAmount(plannedAmount);
        budget.setTotalPlanned(budget.getTotalPlanned() + delta);
        lineRepository.save(line);
        MaintenanceBudget saved = repository.save(budget);
        recordBudgetEvent(
                budget.getId(),
                line.getId(),
                "LINE_REVISED",
                "{\"plannedAmount\":" + oldLineAmount + "}",
                "{\"plannedAmount\":" + plannedAmount + ",\"budgetTotalPlanned\":" + budget.getTotalPlanned() + "}",
                actorUserId,
                comment
        );
        return MaintenanceBudgetDto.from(saved);
    }

    @Transactional(readOnly = true)
    public MaintenanceBudgetDto validateCanApprove(UUID id) {
        MaintenanceBudget b = getOrThrow(id);
        assertCanAccessBudget(b);
        if (b.getStatus() != BudgetStatus.SUBMITTED) {
            throw RestException.badRequest("Only SUBMITTED budgets can be approved");
        }
        return MaintenanceBudgetDto.from(b);
    }

    @Transactional
    public BudgetLineDto addLine(UUID budgetId, BudgetLineDto r) {
        MaintenanceBudget b = getOrThrow(budgetId);
        assertCanAccessBudget(b);
        if (b.getStatus() != BudgetStatus.DRAFT) {
            throw RestException.badRequest("Budget lines can be added only for DRAFT budgets");
        }
        if (r.plannedAmount() <= 0) {
            throw RestException.badRequest("Budget line planned amount must be positive");
        }
        BudgetLine line = new BudgetLine();
        line.setBudget(b);
        line.setCostCategoryId(r.costCategoryId());
        line.setDescription(r.description());
        line.setPlannedAmount(r.plannedAmount());
        b.setTotalPlanned(b.getTotalPlanned() + r.plannedAmount());
        b.getLines().add(line);

        BudgetLine budgetLine = lineRepository.save(line);

        MaintenanceBudget maintenanceBudget = repository.save(b);

        auditBuilderService.log(
                "maintenance_budget",
                maintenanceBudget.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.MAINTENANCE_BUDGET,
                "Бюджет обслуживания обновлен",
                b,
                maintenanceBudget
        );

        return BudgetLineDto.from(budgetLine);
    }

    private MaintenanceBudgetDto changeStatus(MaintenanceBudget budget,
                                              BudgetStatus newStatus,
                                              UUID actorUserId,
                                              String comment,
                                              String eventType) {
        BudgetStatus oldStatus = budget.getStatus();
        budget.setStatus(newStatus);
        MaintenanceBudget saved = repository.save(budget);
        auditBuilderService.log(
                "maintenance_budget",
                saved.getId().toString(),
                AuditAction.UPDATE,
                AuditModule.MAINTENANCE_BUDGET,
                "Бюджет обслуживания обновлен",
                null,
                saved
        );
        recordBudgetEvent(
                saved.getId(),
                null,
                eventType,
                "{\"status\":\"" + oldStatus + "\"}",
                "{\"status\":\"" + newStatus + "\"}",
                actorUserId,
                comment
        );
        return MaintenanceBudgetDto.from(saved);
    }

    private Map<UUID, String> costCategoryNamesById(List<MaintenanceBudget> budgets) {
        Set<UUID> ids = budgets.stream()
                .flatMap(budget -> budget.getLines().stream())
                .map(BudgetLine::getCostCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return costCategoryRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(CostCategory::getId, CostCategory::getName, (left, right) -> left));
    }

    private MaintenanceBudgetDto toDto(
            MaintenanceBudget budget,
            String departmentName,
            Map<UUID, String> costCategoryNames
    ) {
        return MaintenanceBudgetDto.from(budget, departmentName, costCategoryNames);
    }

    private MaintenanceBudget getOrThrow(UUID id) {
        return repository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Budget not found: " + id));
    }

    private BudgetLine budgetLineOrThrow(UUID id) {
        return lineRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Budget line not found: " + id));
    }

    private void assertCanCreateBudget(UUID departmentId) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (departmentId == null || !scopeAccessService.canAccessDepartment(departmentId)) {
            throw forbidden();
        }
    }

    private void assertCanAccessBudget(MaintenanceBudget budget) {
        if (scopeAccessService.isScopeAdmin()) {
            return;
        }
        if (budget.getDepartmentId() == null || !scopeAccessService.canAccessDepartment(budget.getDepartmentId())) {
            throw forbidden();
        }
    }

    private AccessDeniedException forbidden() {
        return new AccessDeniedException("Access denied by budget scope");
    }

    private void assertBudgetCanChangePlan(MaintenanceBudget budget) {
        Set<BudgetStatus> editable = EnumSet.of(BudgetStatus.DRAFT, BudgetStatus.REJECTED, BudgetStatus.APPROVED);
        if (!editable.contains(budget.getStatus())) {
            throw RestException.badRequest("Budget plan cannot be changed from status " + budget.getStatus());
        }
    }

    private void assertLineBelongsToBudget(BudgetLine line, MaintenanceBudget budget) {
        if (line.getBudget() == null || !Objects.equals(line.getBudget().getId(), budget.getId())) {
            throw RestException.badRequest("Budget line does not belong to budget: " + budget.getId());
        }
    }

    private void assertPlannedAmountNotBelowActual(BudgetLine line, double plannedAmount) {
        double minimumPlanned = line.getActualAmount() + line.getCommittedAmount();
        if (plannedAmount + 0.000001d < minimumPlanned) {
            throw RestException.badRequest(
                    "Budget line planned amount cannot be below approved actual and committed amounts");
        }
    }

    private void recordBudgetEvent(UUID budgetId,
                                   UUID budgetLineId,
                                   String eventType,
                                   String oldValues,
                                   String newValues,
                                   UUID actorUserId,
                                   String comment) {
        BudgetEvent event = new BudgetEvent();
        event.setBudgetId(budgetId);
        event.setBudgetLineId(budgetLineId);
        event.setEventType(eventType);
        event.setOldValues(oldValues);
        event.setNewValues(newValues);
        event.setActorUserId(actorUserId);
        event.setComment(comment == null ? null : comment.trim());
        event.setOccurredAt(Instant.now());
        budgetEventRepository.save(event);
    }

    private Comparator<MaintenanceBudget> budgetComparator(String sortBy) {
        return switch (sortBy == null ? "" : sortBy.trim()) {
            case "year" -> Comparator.comparingInt(MaintenanceBudget::getYear);
            case "month", "period" -> Comparator.comparing(
                    MaintenanceBudget::getMonth,
                    Comparator.nullsFirst(Comparator.naturalOrder()));
            case "status" -> Comparator.comparing(
                    budget -> budget.getStatus() != null ? budget.getStatus().name() : "",
                    String.CASE_INSENSITIVE_ORDER);
            case "totalPlanned", "plannedAmount" -> Comparator.comparingDouble(MaintenanceBudget::getTotalPlanned);
            case "totalActual", "actualAmount" -> Comparator.comparingDouble(MaintenanceBudget::getTotalActual);
            case "updatedAt" -> Comparator.comparing(
                    MaintenanceBudget::getUpdatedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder()));
            default -> Comparator.comparing(
                    MaintenanceBudget::getUpdatedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder()));
        };
    }
}

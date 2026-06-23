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
import com.toir.dto.financialreview.ActualCostReviewActivityItem;
import com.toir.dto.financialreview.ActualCostReviewHandoverItem;
import com.toir.dto.financialreview.ActualCostReviewItem;
import com.toir.entity.Department;
import com.toir.entity.users.Employee;
import com.toir.entity.users.User;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.enums.ActualCostStatus;
import com.toir.exception.RestException;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.users.EmployeeRepository;
import com.toir.repository.users.UserRepository;
import com.toir.service.FinanceScopeService;
import com.toir.service.ActualCostReviewFacadeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class BudgetSummaryController {

    private final MaintenanceBudgetRepository budgetRepository;
    private final BudgetLineRepository lineRepository;
    private final ActualCostRepository actualCostRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final FinanceScopeService financeScopeService;
    private final ActualCostReviewFacadeService actualCostReviewFacadeService;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_READ')")
    public ResponseEntity<BudgetSummaryResponse> summary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) UUID departmentId) {
        List<MaintenanceBudget> budgets = financeScopeService.filterBudgets(
                budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
        ).stream()
                .filter(budget -> year == null || budget.getYear() == year)
                .filter(budget -> month == null || Objects.equals(budget.getMonth(), month))
                .filter(budget -> departmentId == null || Objects.equals(budget.getDepartmentId(), departmentId))
                .toList();
        double totalPlanned = budgets.stream().mapToDouble(MaintenanceBudget::getTotalPlanned).sum();
        double totalActual = budgets.stream().mapToDouble(MaintenanceBudget::getTotalActual).sum();
        double variance = totalPlanned - totalActual;
        double executionPercent = totalPlanned > 0 ? (totalActual / totalPlanned) * 100 : 0;

        Set<UUID> budgetIds = budgets.stream().map(MaintenanceBudget::getId).collect(Collectors.toSet());
        Set<UUID> departmentIds = budgets.stream()
                .map(MaintenanceBudget::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Department> departmentsById = departmentIds.isEmpty()
                ? Map.of()
                : departmentRepository.findAllByIdInAndIsDeletedFalse(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, department -> department));

        Map<UUID, CostCategory> catById = costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(CostCategory::getId, c -> c));

        List<BudgetSummaryResponse.CategoryRow> byCategory = financeScopeService
                .filterBudgetLines(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).stream()
                .filter(line -> line.getBudget() != null && budgetIds.contains(line.getBudget().getId()))
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

        List<BudgetSummaryResponse.Item> budgetItems = budgets.stream()
                .map(budget -> toSummaryItem(budget, departmentsById.get(budget.getDepartmentId())))
                .toList();

        return ResponseEntity.ok(new BudgetSummaryResponse(
                budgetItems,
                budgetItems,
                totalPlanned,
                totalActual,
                variance,
                variance,
                executionPercent,
                budgetItems.size(),
                byCategory
        ));
    }

    public ResponseEntity<BudgetSummaryResponse> summary() {
        return summary(null, null, null);
    }

    @GetMapping("/cost-categories")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_READ')")
    public ResponseEntity<Page<CostCategoryDto>> costCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(costCategoryRepository.findAll(search, PageRequest.of(page, size))
                .map(CostCategoryDto::from));
    }

    @GetMapping("/actual-costs/register")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_READ')")
    public ResponseEntity<PageResponseWithSummary<ActualCostReviewItem, ActualCostRegisterSummary>> actualCostRegister(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID costCategoryId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID contractorId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID actualCostId,
            @RequestParam(required = false) String actualCostIds) {
        List<ActualCostReviewItem> items = filterReviewItems(
                actualCostReviewFacadeService.actualCostRegister(search),
                status,
                null,
                null,
                costCategoryId,
                departmentId,
                contractorId,
                null,
                null,
                parseDateStart(dateFrom),
                parseDateEnd(dateTo),
                actualCostId,
                actualCostIds
        );
        return ResponseEntity.ok(PageResponseWithSummary.of(
                items,
                page,
                size,
                actualCostReviewFacadeService.registerSummary(items)
        ));
    }

    public ResponseEntity<PageResponseWithSummary<ActualCostReviewItem, ActualCostRegisterSummary>> actualCostRegister(
            int page,
            int size,
            String search,
            String status,
            UUID costCategoryId,
            String dateFrom,
            String dateTo,
            UUID actualCostId,
            String actualCostIds) {
        return actualCostRegister(page, size, search, status, costCategoryId, null, null, dateFrom, dateTo, actualCostId, actualCostIds);
    }

    @GetMapping("/actual-costs/review-queue")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_READ')")
    public ResponseEntity<PageResponse<ActualCostReviewItem>> reviewQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(required = false) Boolean myQueue,
            @RequestParam(required = false) String attentionMode,
            @RequestParam(required = false) Integer reminderWindowHours,
            @RequestParam(required = false) String approvalRoleCode,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID contractorId,
            @RequestParam(required = false) UUID actualCostId,
            @RequestParam(required = false) String actualCostIds) {
        List<ActualCostReviewItem> pending = filterReviewItems(
                actualCostReviewFacadeService.reviewQueue(search),
                status,
                Boolean.TRUE.equals(overdueOnly),
                approvalRoleCode,
                null,
                departmentId,
                contractorId,
                attentionMode,
                reminderWindowHours,
                null,
                null,
                actualCostId,
                actualCostIds
        );
        return ResponseEntity.ok(PageResponse.of(
                pending,
                page,
                size
        ));
    }

    @GetMapping("/actual-costs/{id}/review-history")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_READ')")
    public ResponseEntity<ActualCostReviewHistoryResponse> reviewHistory(@PathVariable UUID id) {
        ActualCost actualCost = actualCostRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Actual cost not found: " + id));
        financeScopeService.assertCanReadActualCost(actualCost);

        List<ActualCostReviewHistoryResponse.Event> events = actualCost.getReviewedAt() != null
                ? List.of(new ActualCostReviewHistoryResponse.Event(
                actualCost.getId(),
                actualCost.getReviewedAt(),
                actualCost.getStatus() != null ? actualCost.getStatus().name() : null,
                actualCost.getReviewedById(),
                toUserRef(actualCost.getReviewedById()),
                actualCost.getReviewComment() != null ? actualCost.getReviewComment() : ""
        ))
                : List.of();

        ActualCostReviewHistoryResponse response = new ActualCostReviewHistoryResponse(
                actualCost.getId().toString(),
                new ActualCostReviewHistoryResponse.ActualCostRef(
                        actualCost.getId(),
                        actualCost.getStatus() != null ? actualCost.getStatus().name() : null,
                        actualCost.getAmount(),
                        actualCost.getCostDate(),
                        actualCost.getReviewedAt(),
                        actualCost.getReviewedById(),
                        actualCost.getReviewComment() != null ? actualCost.getReviewComment() : ""
                ),
                events
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/actual-costs/review-activity")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_READ')")
    public ResponseEntity<PageResponseWithSummary<ActualCostReviewActivityItem, ActualCostReviewActivitySummary>> reviewActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String eventGroup,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) UUID actualCostId,
            @RequestParam(required = false) String actualCostIds) {
        List<ActualCostReviewActivityItem> events = filterActivityItems(
                actualCostReviewFacadeService.activity(search),
                eventGroup,
                departmentId,
                roleCode,
                actualCostId,
                actualCostIds
        );
        return ResponseEntity.ok(PageResponseWithSummary.of(
                events,
                page,
                size,
                actualCostReviewFacadeService.activitySummary(events)
        ));
    }

    @GetMapping("/actual-costs/handovers")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_READ')")
    public ResponseEntity<PageResponseWithSummary<ActualCostReviewHandoverItem, ActualCostHandoverSummary>> handovers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String approvalRoleCode,
            @RequestParam(required = false) UUID actualCostId,
            @RequestParam(required = false) String actualCostIds) {
        List<ActualCostReviewHandoverItem> handovers = filterHandoverItems(
                actualCostReviewFacadeService.handovers(search),
                departmentId,
                approvalRoleCode,
                actualCostId,
                actualCostIds
        );
        return ResponseEntity.ok(PageResponseWithSummary.of(
                handovers,
                page,
                size,
                actualCostReviewFacadeService.handoverSummary(handovers)
        ));
    }

    @GetMapping("/actual-costs/approval-pack")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_READ')")
    public ResponseEntity<PageResponse<ActualCostBudgetRow>> approvalPack(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        List<ActualCost> pending = financeScopeService.filterActualCosts(
                actualCostRepository.findAllByStatusAndIsDeletedFalseOrderByUpdatedAtDesc(ActualCostStatus.PENDING)
        );
        Map<UUID, String> reviewerNames = reviewerNamesById(pending);
        return ResponseEntity.ok(PageResponse.of(
                pending.stream().map(c -> actualCostRow(c, reviewerNames)).toList(),
                page,
                size
        ));
    }

    @GetMapping("/actual-costs/review-history-pack")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_READ')")
    public ResponseEntity<PageResponse<ActualCostBudgetRow>> reviewHistoryPack(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        List<ActualCost> reviewed = financeScopeService
                .filterActualCosts(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).stream()
                .filter(c -> c.getReviewedAt() != null)
                .toList();
        Map<UUID, String> reviewerNames = reviewerNamesById(reviewed);
        return ResponseEntity.ok(PageResponse.of(
                reviewed.stream().map(c -> actualCostRow(c, reviewerNames)).toList(),
                page,
                size
        ));
    }

    @GetMapping("/contractor-works/{id}/recommendation")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BUDGET_READ')")
    public ResponseEntity<ContractorWorkRecommendationResponse> contractorWorkRecommendation(@PathVariable UUID id) {
        return ResponseEntity.ok(new ContractorWorkRecommendationResponse(id.toString(), 0, List.of()));
    }

    private ActualCostBudgetRow actualCostRow(ActualCost c, Map<UUID, String> reviewerNames) {
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
                c.getReviewedById() != null ? reviewerNames.get(c.getReviewedById()) : null,
                c.getReviewComment()
        );
    }

    private BudgetSummaryResponse.Item toSummaryItem(MaintenanceBudget budget, Department department) {
        return new BudgetSummaryResponse.Item(
                budget.getId(),
                budget.getYear(),
                budget.getMonth(),
                budget.getStatus() != null ? budget.getStatus().name() : null,
                budget.getTotalPlanned(),
                budget.getTotalActual(),
                department != null
                        ? new BudgetSummaryResponse.DepartmentRef(department.getId(), department.getCode(), department.getName())
                        : null
        );
    }

    private List<ActualCostReviewItem> filterReviewItems(List<ActualCostReviewItem> items,
                                                         String status,
                                                         Boolean overdueOnly,
                                                         String approvalRoleCode,
                                                         UUID costCategoryId,
                                                         UUID departmentId,
                                                         UUID contractorId,
                                                         String attentionMode,
                                                         Integer reminderWindowHours,
                                                         Instant dateFrom,
                                                         Instant dateTo,
                                                         UUID actualCostId,
                                                         String actualCostIds) {
        Set<UUID> scopedIds = parseActualCostIds(actualCostId, actualCostIds);
        return items.stream()
                .filter(item -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                        || status.equalsIgnoreCase(item.status()))
                .filter(item -> overdueOnly == null || !overdueOnly || item.isOverdue())
                .filter(item -> approvalRoleCode == null || approvalRoleCode.isBlank()
                        || approvalRoleCode.equalsIgnoreCase(item.approvalRoleCode()))
                .filter(item -> costCategoryId == null || costCategoryId.equals(item.costCategoryId()))
                .filter(item -> departmentId == null || matchesDepartment(item.department(), item.workOrder(), departmentId))
                .filter(item -> contractorId == null || contractorId.equals(contractorId(item)))
                .filter(item -> matchesAttention(item, attentionMode, reminderWindowHours))
                .filter(item -> dateFrom == null || item.costDate() == null || !item.costDate().isBefore(dateFrom))
                .filter(item -> dateTo == null || item.costDate() == null || item.costDate().isBefore(dateTo))
                .filter(item -> scopedIds.isEmpty() || scopedIds.contains(item.id()))
                .toList();
    }

    private List<ActualCostReviewActivityItem> filterActivityItems(List<ActualCostReviewActivityItem> items,
                                                                   String eventGroup,
                                                                   UUID departmentId,
                                                                   String roleCode,
                                                                   UUID actualCostId,
                                                                   String actualCostIds) {
        Set<UUID> scopedIds = parseActualCostIds(actualCostId, actualCostIds);
        return items.stream()
                .filter(item -> eventGroup == null || eventGroup.isBlank() || eventGroup.equalsIgnoreCase(item.eventGroup()))
                .filter(item -> departmentId == null || matchesDepartment(item.department(), item.workOrder(), departmentId))
                .filter(item -> roleCode == null || roleCode.isBlank()
                        || roleCode.equalsIgnoreCase(item.recipientRoleCode())
                        || roleCode.equalsIgnoreCase(item.approvalRoleCode()))
                .filter(item -> scopedIds.isEmpty() || scopedIds.contains(item.actualCostId()))
                .toList();
    }

    private List<ActualCostReviewHandoverItem> filterHandoverItems(List<ActualCostReviewHandoverItem> items,
                                                                  UUID departmentId,
                                                                  String approvalRoleCode,
                                                                  UUID actualCostId,
                                                                  String actualCostIds) {
        Set<UUID> scopedIds = parseActualCostIds(actualCostId, actualCostIds);
        return items.stream()
                .filter(item -> departmentId == null || matchesDepartment(item.department(), item.workOrder(), departmentId))
                .filter(item -> approvalRoleCode == null || approvalRoleCode.isBlank()
                        || approvalRoleCode.equalsIgnoreCase(item.nextApprovalRoleCode())
                        || approvalRoleCode.equalsIgnoreCase(item.previousApprovalRoleCode()))
                .filter(item -> scopedIds.isEmpty() || scopedIds.contains(item.actualCostId()))
                .toList();
    }

    private boolean matchesAttention(ActualCostReviewItem item, String attentionMode, Integer reminderWindowHours) {
        if (attentionMode == null || attentionMode.isBlank() || "ALL".equalsIgnoreCase(attentionMode)) {
            return true;
        }
        if ("OVERDUE".equalsIgnoreCase(attentionMode)) {
            return item.isOverdue();
        }
        if ("DUE_SOON".equalsIgnoreCase(attentionMode)) {
            int reminderWindow = reminderWindowHours != null ? reminderWindowHours : 4;
            return !item.isOverdue() && item.hoursToOverdue() <= reminderWindow;
        }
        return true;
    }

    private boolean matchesDepartment(Object department, Object workOrder, UUID departmentId) {
        UUID directDepartmentId = objectId(department);
        if (departmentId.equals(directDepartmentId)) {
            return true;
        }
        Object workOrderDepartment = objectProperty(workOrder, "department");
        return departmentId.equals(objectId(workOrderDepartment));
    }

    private UUID contractorId(ActualCostReviewItem item) {
        Object contractorWork = item.contractorWork();
        Object contractor = objectProperty(contractorWork, "contractor");
        return objectId(contractor);
    }

    private UUID objectId(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        Object nestedId = objectProperty(value, "id");
        if (nestedId == value) {
            return null;
        }
        return objectId(nestedId);
    }

    private Object objectProperty(Object value, String name) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return map.get(name);
        }
        try {
            return value.getClass().getMethod(name).invoke(value);
        } catch (ReflectiveOperationException | SecurityException ex) {
            return null;
        }
    }

    private Set<UUID> parseActualCostIds(UUID actualCostId, String actualCostIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (actualCostId != null) {
            ids.add(actualCostId);
        }
        if (actualCostIds != null && !actualCostIds.isBlank()) {
            for (String rawId : actualCostIds.split(",")) {
                if (!rawId.isBlank()) {
                    ids.add(UUID.fromString(rawId.trim()));
                }
            }
        }
        return ids;
    }

    private Instant parseDateStart(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant parseDateEnd(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Map<UUID, String> reviewerNamesById(List<ActualCost> costs) {
        Set<UUID> reviewerIds = costs.stream()
                .map(ActualCost::getReviewedById)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (reviewerIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllByIdInAndIsDeletedFalse(reviewerIds)
                .forEach(user -> names.put(user.getId(), user.getFullName()));
        employeeRepository.findAllByIdInAndIsDeletedFalse(reviewerIds)
                .forEach(employee -> names.putIfAbsent(employee.getId(), employeeName(employee)));
        return names;
    }

    private String employeeName(Employee employee) {
        return Stream.of(employee.getLastName(), employee.getFirstName(), employee.getMiddleName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    private ActualCostReviewHistoryResponse.UserRef toUserRef(UUID userId) {
        if (userId == null) {
            return new ActualCostReviewHistoryResponse.UserRef(null, null);
        }
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .map(User::getFullName)
                .map(fullName -> new ActualCostReviewHistoryResponse.UserRef(userId, fullName))
                .orElse(new ActualCostReviewHistoryResponse.UserRef(userId, null));
    }
}

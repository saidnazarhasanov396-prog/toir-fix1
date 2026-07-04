package com.toir.service;

import com.toir.dto.budget.FinanceDashboardResponse;
import com.toir.finance.FinanceBudgetMath;
import com.toir.dto.budget.FinanceReportRow;
import com.toir.entity.Department;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.BudgetLine;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.projects.MaintenanceBudget;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.department.DepartmentRepository;
import com.toir.repository.maintenance.MaintenanceBudgetRepository;
import com.toir.repository.projects.BudgetLineRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.util.CsvWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceReportService {

    private final MaintenanceBudgetRepository budgetRepository;
    private final BudgetLineRepository lineRepository;
    private final ActualCostRepository actualCostRepository;
    private final DepartmentRepository departmentRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final WorkOrderRepository workOrderRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final FinanceScopeService financeScopeService;

    @Transactional(readOnly = true)
    public FinanceDashboardResponse dashboard(Integer year, Integer month, UUID departmentId) {
        FinanceDataset dataset = dataset(year, month, departmentId);
        Totals totals = totals(dataset.actualCosts(), dataset.lines());
        List<FinanceReportRow> byDepartment = byDepartment(dataset);
        List<FinanceReportRow> byCategory = byCategory(dataset);
        return new FinanceDashboardResponse(
                totals.plannedAmount,
                totals.committedAmount,
                totals.approvedActualAmount,
                totals.pendingActualAmount,
                totals.rejectedActualAmount,
                totals.remainingBudget(),
                totals.forecastRemaining(),
                totals.variance(),
                totals.burnRate(),
                totals.riskAmount(),
                totals.unallocatedAmount,
                Instant.now(),
                new FinanceDashboardResponse.Filters(year, month, departmentId),
                byDepartment,
                byCategory
        );
    }

    @Transactional(readOnly = true)
    public List<FinanceReportRow> planVsActualByDepartment(Integer year, Integer month, UUID departmentId) {
        return byDepartment(dataset(year, month, departmentId));
    }

    @Transactional(readOnly = true)
    public List<FinanceReportRow> planVsActualByCategory(Integer year, Integer month, UUID departmentId) {
        return byCategory(dataset(year, month, departmentId));
    }

    @Transactional(readOnly = true)
    public ReportsService.CsvFile planVsActualByDepartmentCsv(Integer year, Integer month, UUID departmentId) {
        List<FinanceReportRow> rows = planVsActualByDepartment(year, month, departmentId);
        return new ReportsService.CsvFile(
                "finance-plan-vs-actual-by-department.csv",
                reportCsv(rows, year, month, departmentId)
        );
    }

    @Transactional(readOnly = true)
    public ReportsService.CsvFile planVsActualByCategoryCsv(Integer year, Integer month, UUID departmentId) {
        List<FinanceReportRow> rows = planVsActualByCategory(year, month, departmentId);
        return new ReportsService.CsvFile(
                "finance-plan-vs-actual-by-category.csv",
                reportCsv(rows, year, month, departmentId)
        );
    }

    private FinanceDataset dataset(Integer year, Integer month, UUID departmentId) {
        List<MaintenanceBudget> budgets = financeScopeService
                .filterBudgets(nullSafe(budgetRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()))
                .stream()
                .filter(budget -> year == null || budget.getYear() == year)
                .filter(budget -> month == null || Objects.equals(budget.getMonth(), month))
                .filter(budget -> departmentId == null || Objects.equals(budget.getDepartmentId(), departmentId))
                .toList();
        Set<UUID> budgetIds = budgets.stream()
                .map(MaintenanceBudget::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, MaintenanceBudget> budgetById = budgets.stream()
                .filter(budget -> budget.getId() != null)
                .collect(Collectors.toMap(MaintenanceBudget::getId, Function.identity(), (a, b) -> a));
        List<BudgetLine> lines = financeScopeService
                .filterBudgetLines(nullSafe(lineRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()))
                .stream()
                .filter(line -> line.getBudget() != null && budgetIds.contains(line.getBudget().getId()))
                .toList();
        Map<UUID, BudgetLine> lineById = lines.stream()
                .filter(line -> line.getId() != null)
                .collect(Collectors.toMap(BudgetLine::getId, Function.identity(), (a, b) -> a));
        SourceMaps sourceMaps = sourceMaps();
        List<ActualCost> actualCosts = financeScopeService
                .filterActualCosts(nullSafe(actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()))
                .stream()
                .filter(cost -> matchesPeriod(cost, year, month))
                .filter(cost -> matchesDepartment(cost, departmentId, lineById, sourceMaps))
                .filter(cost -> cost.getBudgetLineId() == null || lineById.containsKey(cost.getBudgetLineId()))
                .toList();

        Map<UUID, Department> departments = departmentsById(budgets, actualCosts, lineById, sourceMaps, departmentId);
        Map<UUID, CostCategory> categories = costCategoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
                .stream()
                .collect(Collectors.toMap(CostCategory::getId, Function.identity(), (a, b) -> a));
        return new FinanceDataset(budgets, budgetById, lines, lineById, actualCosts, departments, categories, sourceMaps, departmentId);
    }

    private List<FinanceReportRow> byDepartment(FinanceDataset dataset) {
        Map<UUID, MutableTotals> totalsByDepartment = new LinkedHashMap<>();
        for (BudgetLine line : dataset.lines()) {
            UUID departmentId = line.getBudget() != null ? line.getBudget().getDepartmentId() : null;
            MutableTotals departmentTotals = totalsByDepartment.computeIfAbsent(departmentId, key -> new MutableTotals());
            departmentTotals.plannedAmount += line.getPlannedAmount();
            departmentTotals.committedAmount += line.getCommittedAmount();
        }
        for (ActualCost cost : dataset.actualCosts()) {
            UUID departmentId = resolveDepartmentId(cost, dataset.lineById(), dataset.sourceMaps());
            if (departmentId == null && dataset.requestedDepartmentId() != null) {
                departmentId = dataset.requestedDepartmentId();
            }
            addActualCost(totalsByDepartment.computeIfAbsent(departmentId, key -> new MutableTotals()), cost);
        }
        return totalsByDepartment.entrySet().stream()
                .map(entry -> {
                    Department department = entry.getKey() != null ? dataset.departments().get(entry.getKey()) : null;
                    return row(
                            entry.getKey(),
                            department != null ? department.getCode() : "UNALLOCATED",
                            department != null ? department.getName() : "Unallocated / unresolved",
                            "DEPARTMENT",
                            entry.getValue()
                    );
                })
                .sorted(rowComparator())
                .toList();
    }

    private List<FinanceReportRow> byCategory(FinanceDataset dataset) {
        Map<UUID, MutableTotals> totalsByCategory = new LinkedHashMap<>();
        for (BudgetLine line : dataset.lines()) {
            MutableTotals categoryTotals = totalsByCategory.computeIfAbsent(line.getCostCategoryId(), key -> new MutableTotals());
            categoryTotals.plannedAmount += line.getPlannedAmount();
            categoryTotals.committedAmount += line.getCommittedAmount();
        }
        for (ActualCost cost : dataset.actualCosts()) {
            UUID categoryId = resolveReportCategoryId(cost, dataset.lineById());
            addActualCost(totalsByCategory.computeIfAbsent(categoryId, key -> new MutableTotals()), cost);
        }
        return totalsByCategory.entrySet().stream()
                .map(entry -> {
                    CostCategory category = entry.getKey() != null ? dataset.categories().get(entry.getKey()) : null;
                    return row(
                            entry.getKey(),
                            category != null ? category.getCode() : "UNALLOCATED",
                            category != null ? category.getName() : "Unallocated / uncategorized",
                            "CATEGORY",
                            entry.getValue()
                    );
                })
                .sorted(rowComparator())
                .toList();
    }

    private Totals totals(List<ActualCost> actualCosts, List<BudgetLine> lines) {
        MutableTotals totals = new MutableTotals();
        lines.forEach(line -> {
            totals.plannedAmount += line.getPlannedAmount();
            totals.committedAmount += line.getCommittedAmount();
        });
        actualCosts.forEach(cost -> addActualCost(totals, cost));
        return totals.toTotals();
    }

    private void addActualCost(MutableTotals totals, ActualCost cost) {
        totals.actualCostCount += 1;
        if (cost.getBudgetLineId() == null) {
            totals.unallocatedAmount += cost.getAmount();
        }
        if (cost.getStatus() == ActualCostStatus.APPROVED) {
            totals.approvedActualAmount += cost.getAmount();
        } else if (cost.getStatus() == ActualCostStatus.PENDING) {
            totals.pendingActualAmount += cost.getAmount();
        } else if (cost.getStatus() == ActualCostStatus.REJECTED) {
            totals.rejectedActualAmount += cost.getAmount();
        }
    }

    private FinanceReportRow row(UUID groupId, String groupCode, String groupName, String groupType, MutableTotals mutable) {
        Totals totals = mutable.toTotals();
        return new FinanceReportRow(
                groupId,
                groupCode,
                groupName,
                groupType,
                totals.plannedAmount,
                totals.committedAmount,
                totals.approvedActualAmount,
                totals.pendingActualAmount,
                totals.rejectedActualAmount,
                totals.remainingBudget(),
                totals.forecastRemaining(),
                totals.variance(),
                totals.burnRate(),
                totals.riskAmount(),
                totals.unallocatedAmount,
                totals.actualCostCount
        );
    }

    private String reportCsv(List<FinanceReportRow> rows, Integer year, Integer month, UUID departmentId) {
        Instant generatedAt = Instant.now();
        List<ExportRow> exportRows = rows.stream()
                .map(row -> new ExportRow(generatedAt, year, month, departmentId, row))
                .toList();
        return CsvWriter.build(
                List.of(
                        "generatedAt",
                        "filterYear",
                        "filterMonth",
                        "filterDepartmentId",
                        "groupType",
                        "groupId",
                        "groupCode",
                        "groupName",
                        "plannedAmount",
                        "committedAmount",
                        "approvedActualAmount",
                        "pendingActualAmount",
                        "rejectedActualAmount",
                        "remainingBudget",
                        "forecastRemaining",
                        "variance",
                        "burnRate",
                        "riskAmount",
                        "unallocatedAmount",
                        "actualCostCount"
                ),
                exportRows,
                List.of(
                        ExportRow::generatedAt,
                        ExportRow::filterYear,
                        ExportRow::filterMonth,
                        ExportRow::filterDepartmentId,
                        row -> row.reportRow().groupType(),
                        row -> row.reportRow().groupId(),
                        row -> row.reportRow().groupCode(),
                        row -> row.reportRow().groupName(),
                        row -> row.reportRow().plannedAmount(),
                        row -> row.reportRow().committedAmount(),
                        row -> row.reportRow().approvedActualAmount(),
                        row -> row.reportRow().pendingActualAmount(),
                        row -> row.reportRow().rejectedActualAmount(),
                        row -> row.reportRow().remainingBudget(),
                        row -> row.reportRow().forecastRemaining(),
                        row -> row.reportRow().variance(),
                        row -> row.reportRow().burnRate(),
                        row -> row.reportRow().riskAmount(),
                        row -> row.reportRow().unallocatedAmount(),
                        row -> row.reportRow().actualCostCount()
                )
        );
    }

    private SourceMaps sourceMaps() {
        Map<UUID, WorkOrder> workOrders = nullSafe(workOrderRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .stream()
                .filter(workOrder -> workOrder.getId() != null)
                .collect(Collectors.toMap(WorkOrder::getId, Function.identity(), (a, b) -> a));
        Map<UUID, RepairRequest> repairRequests = nullSafe(repairRequestRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .stream()
                .filter(request -> request.getId() != null)
                .collect(Collectors.toMap(RepairRequest::getId, Function.identity(), (a, b) -> a));
        Map<UUID, ContractorWork> contractorWorks = nullSafe(contractorWorkRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc())
                .stream()
                .filter(work -> work.getId() != null)
                .collect(Collectors.toMap(ContractorWork::getId, Function.identity(), (a, b) -> a));
        return new SourceMaps(workOrders, repairRequests, contractorWorks);
    }

    private Map<UUID, Department> departmentsById(List<MaintenanceBudget> budgets,
                                                 List<ActualCost> actualCosts,
                                                 Map<UUID, BudgetLine> lineById,
                                                 SourceMaps sourceMaps,
                                                 UUID requestedDepartmentId) {
        Set<UUID> departmentIds = new java.util.LinkedHashSet<>();
        budgets.stream().map(MaintenanceBudget::getDepartmentId).filter(Objects::nonNull).forEach(departmentIds::add);
        actualCosts.stream()
                .map(cost -> resolveDepartmentId(cost, lineById, sourceMaps))
                .filter(Objects::nonNull)
                .forEach(departmentIds::add);
        if (requestedDepartmentId != null) {
            departmentIds.add(requestedDepartmentId);
        }
        if (departmentIds.isEmpty()) {
            return Map.of();
        }
        return departmentRepository.findAllByIdInAndIsDeletedFalse(departmentIds)
                .stream()
                .collect(Collectors.toMap(Department::getId, Function.identity(), (a, b) -> a));
    }

    private boolean matchesPeriod(ActualCost cost, Integer year, Integer month) {
        if (cost.getCostDate() == null || (year == null && month == null)) {
            return true;
        }
        var costDate = cost.getCostDate().atZone(ZoneOffset.UTC).toLocalDate();
        return (year == null || costDate.getYear() == year)
                && (month == null || costDate.getMonthValue() == month);
    }

    private boolean matchesDepartment(ActualCost cost,
                                      UUID requestedDepartmentId,
                                      Map<UUID, BudgetLine> lineById,
                                      SourceMaps sourceMaps) {
        if (requestedDepartmentId == null) {
            return true;
        }
        UUID departmentId = resolveDepartmentId(cost, lineById, sourceMaps);
        return departmentId == null || requestedDepartmentId.equals(departmentId);
    }

    private UUID resolveDepartmentId(ActualCost cost, Map<UUID, BudgetLine> lineById, SourceMaps sourceMaps) {
        if (cost.getBudgetLineId() != null) {
            BudgetLine line = lineById.get(cost.getBudgetLineId());
            if (line != null && line.getBudget() != null && line.getBudget().getDepartmentId() != null) {
                return line.getBudget().getDepartmentId();
            }
        }
        if (cost.getWorkOrderId() != null) {
            WorkOrder workOrder = sourceMaps.workOrders().get(cost.getWorkOrderId());
            if (workOrder != null) {
                return workOrder.getDepartmentId();
            }
        }
        if (cost.getRepairRequestId() != null) {
            RepairRequest repairRequest = sourceMaps.repairRequests().get(cost.getRepairRequestId());
            if (repairRequest != null) {
                return repairRequest.getDepartmentId();
            }
        }
        if (cost.getContractorWorkId() != null) {
            ContractorWork contractorWork = sourceMaps.contractorWorks().get(cost.getContractorWorkId());
            if (contractorWork != null && contractorWork.getWorkOrderId() != null) {
                WorkOrder workOrder = sourceMaps.workOrders().get(contractorWork.getWorkOrderId());
                if (workOrder != null) {
                    return workOrder.getDepartmentId();
                }
            }
        }
        return null;
    }

    private UUID resolveReportCategoryId(ActualCost cost, Map<UUID, BudgetLine> lineById) {
        if (cost.getBudgetLineId() != null) {
            BudgetLine line = lineById.get(cost.getBudgetLineId());
            if (line != null && line.getCostCategoryId() != null) {
                return line.getCostCategoryId();
            }
        }
        return cost.getCostCategoryId();
    }

    private Comparator<FinanceReportRow> rowComparator() {
        return Comparator.comparing(
                FinanceReportRow::groupName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
        );
    }

    private <T> List<T> nullSafe(Collection<T> items) {
        if (items == null) {
            return List.of();
        }
        if (items instanceof List<T> list) {
            return list;
        }
        return new ArrayList<>(items);
    }

    private record FinanceDataset(
            List<MaintenanceBudget> budgets,
            Map<UUID, MaintenanceBudget> budgetById,
            List<BudgetLine> lines,
            Map<UUID, BudgetLine> lineById,
            List<ActualCost> actualCosts,
            Map<UUID, Department> departments,
            Map<UUID, CostCategory> categories,
            SourceMaps sourceMaps,
            UUID requestedDepartmentId
    ) {
    }

    private record SourceMaps(
            Map<UUID, WorkOrder> workOrders,
            Map<UUID, RepairRequest> repairRequests,
            Map<UUID, ContractorWork> contractorWorks
    ) {
    }

    private record ExportRow(
            Instant generatedAt,
            Integer filterYear,
            Integer filterMonth,
            UUID filterDepartmentId,
            FinanceReportRow reportRow
    ) {
    }

    private static class MutableTotals {
        private double plannedAmount;
        private double committedAmount;
        private double approvedActualAmount;
        private double pendingActualAmount;
        private double rejectedActualAmount;
        private double unallocatedAmount;
        private long actualCostCount;

        private Totals toTotals() {
            return new Totals(
                    plannedAmount,
                    committedAmount,
                    approvedActualAmount,
                    pendingActualAmount,
                    rejectedActualAmount,
                    unallocatedAmount,
                    actualCostCount
            );
        }
    }

    private record Totals(
            double plannedAmount,
            double committedAmount,
            double approvedActualAmount,
            double pendingActualAmount,
            double rejectedActualAmount,
            double unallocatedAmount,
            long actualCostCount
    ) {
        private double remainingBudget() {
            return FinanceBudgetMath.remainingBudget(plannedAmount, approvedActualAmount, committedAmount);
        }

        private double forecastRemaining() {
            return plannedAmount - approvedActualAmount - pendingActualAmount;
        }

        private double variance() {
            return FinanceBudgetMath.variance(plannedAmount, approvedActualAmount);
        }

        private double burnRate() {
            return FinanceBudgetMath.burnRate(plannedAmount, approvedActualAmount);
        }

        private double riskAmount() {
            return FinanceBudgetMath.riskAmount(
                    plannedAmount, approvedActualAmount, pendingActualAmount, committedAmount);
        }
    }
}

package com.toir.service;

import com.toir.dto.equipmentcost.EquipmentCostDrilldownResponse;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.exception.RestException;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EquipmentCostDrilldownService {

    private static final String DEFAULT_CURRENCY = "UZS";
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final EquipmentRepository equipmentRepository;
    private final ActualCostRepository actualCostRepository;
    private final WorkOrderRepository workOrderRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final FinanceScopeService financeScopeService;

    @Transactional(readOnly = true)
    public EquipmentCostDrilldownResponse getDrilldown(UUID equipmentId,
                                                       LocalDate from,
                                                       LocalDate to,
                                                       UUID categoryId,
                                                       ActualCostSourceType sourceType) {
        if (equipmentId == null) {
            throw RestException.badRequest("equipmentId is required");
        }
        if (!equipmentRepository.existsByIdAndIsDeletedFalse(equipmentId)) {
            throw RestException.notFound("Equipment not found: " + equipmentId);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw RestException.badRequest("from must be on or before to");
        }

        Instant fromInstant = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<ActualCost> scopedCosts = financeScopeService.filterActualCosts(
                actualCostRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()
        );
        List<ActualCost> filteredCosts = scopedCosts.stream()
                .filter(cost -> withinPeriod(cost, fromInstant, toExclusive))
                .filter(cost -> categoryId == null || categoryId.equals(cost.getCostCategoryId()))
                .filter(cost -> sourceType == null || sourceType == cost.getSourceType())
                .toList();

        Map<UUID, ContractorWork> contractorWorks = loadContractorWorks(filteredCosts);
        Map<UUID, WorkOrder> workOrders = loadWorkOrders(filteredCosts, contractorWorks);
        Map<UUID, RepairRequest> repairRequests = loadRepairRequests(filteredCosts);

        List<ActualCost> equipmentCosts = filteredCosts.stream()
                .filter(cost -> isForEquipment(cost, equipmentId, workOrders, repairRequests, contractorWorks))
                .sorted(Comparator.comparing(this::costSortInstant, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<UUID, CostCategory> categories = loadCategories(equipmentCosts);
        List<EquipmentCostDrilldownResponse.Row> rows = equipmentCosts.stream()
                .map(cost -> toRow(cost, categories, workOrders, repairRequests, contractorWorks))
                .toList();

        return new EquipmentCostDrilldownResponse(
                equipmentId,
                DEFAULT_CURRENCY,
                sumTotal(rows),
                sumByStatus(rows, ActualCostStatus.APPROVED),
                sumByStatus(rows, ActualCostStatus.PENDING),
                sumByStatus(rows, ActualCostStatus.REJECTED),
                buckets(rows, row -> row.category() == null || row.category().id() == null
                                ? "UNCATEGORIZED"
                                : row.category().id().toString(),
                        row -> row.category() == null
                                ? "Uncategorized"
                                : displayCategory(row.category())),
                buckets(rows, row -> row.sourceType() == null ? "UNSPECIFIED" : row.sourceType().name(),
                        row -> sourceTypeLabel(row.sourceType())),
                buckets(rows, row -> row.createdAt() == null ? "UNKNOWN" : MONTH_FORMAT.format(row.createdAt()),
                        row -> row.createdAt() == null ? "Unknown" : MONTH_FORMAT.format(row.createdAt())),
                rows
        );
    }

    private boolean withinPeriod(ActualCost cost, Instant fromInstant, Instant toExclusive) {
        Instant date = cost.getCostDate() != null ? cost.getCostDate() : cost.getCreatedAt();
        if (date == null) {
            return true;
        }
        if (fromInstant != null && date.isBefore(fromInstant)) {
            return false;
        }
        return toExclusive == null || date.isBefore(toExclusive);
    }

    private Map<UUID, ContractorWork> loadContractorWorks(List<ActualCost> costs) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (ActualCost cost : costs) {
            if (cost.getContractorWorkId() != null) {
                ids.add(cost.getContractorWorkId());
            }
            if (cost.getSourceType() == ActualCostSourceType.CONTRACTOR_WORK && cost.getSourceId() != null) {
                ids.add(cost.getSourceId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return contractorWorkRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(ContractorWork::getId, Function.identity()));
    }

    private Map<UUID, WorkOrder> loadWorkOrders(List<ActualCost> costs, Map<UUID, ContractorWork> contractorWorks) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (ActualCost cost : costs) {
            UUID workOrderId = directWorkOrderId(cost);
            if (workOrderId != null) {
                ids.add(workOrderId);
            }
            UUID contractorWorkId = directContractorWorkId(cost);
            ContractorWork contractorWork = contractorWorkId == null ? null : contractorWorks.get(contractorWorkId);
            if (contractorWork != null && contractorWork.getWorkOrderId() != null) {
                ids.add(contractorWork.getWorkOrderId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return workOrderRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(WorkOrder::getId, Function.identity()));
    }

    private Map<UUID, RepairRequest> loadRepairRequests(List<ActualCost> costs) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (ActualCost cost : costs) {
            if (cost.getRepairRequestId() != null) {
                ids.add(cost.getRepairRequestId());
            }
            if (cost.getSourceType() == ActualCostSourceType.REPAIR_REQUEST && cost.getSourceId() != null) {
                ids.add(cost.getSourceId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repairRequestRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(RepairRequest::getId, Function.identity()));
    }

    private Map<UUID, CostCategory> loadCategories(List<ActualCost> costs) {
        LinkedHashSet<UUID> ids = costs.stream()
                .map(ActualCost::getCostCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return costCategoryRepository.findAllByIdInAndIsDeletedFalse(ids).stream()
                .collect(Collectors.toMap(CostCategory::getId, Function.identity()));
    }

    private boolean isForEquipment(ActualCost cost,
                                   UUID equipmentId,
                                   Map<UUID, WorkOrder> workOrders,
                                   Map<UUID, RepairRequest> repairRequests,
                                   Map<UUID, ContractorWork> contractorWorks) {
        UUID workOrderId = resolvedWorkOrderId(cost, contractorWorks);
        if (workOrderId != null) {
            WorkOrder workOrder = workOrders.get(workOrderId);
            if (workOrder != null && equipmentId.equals(workOrder.getEquipmentId())) {
                return true;
            }
        }

        UUID repairRequestId = directRepairRequestId(cost);
        if (repairRequestId != null) {
            RepairRequest repairRequest = repairRequests.get(repairRequestId);
            return repairRequest != null && equipmentId.equals(repairRequest.getEquipmentId());
        }

        return false;
    }

    private EquipmentCostDrilldownResponse.Row toRow(ActualCost cost,
                                                     Map<UUID, CostCategory> categories,
                                                     Map<UUID, WorkOrder> workOrders,
                                                     Map<UUID, RepairRequest> repairRequests,
                                                     Map<UUID, ContractorWork> contractorWorks) {
        UUID contractorWorkId = directContractorWorkId(cost);
        UUID workOrderId = resolvedWorkOrderId(cost, contractorWorks);
        UUID repairRequestId = directRepairRequestId(cost);
        CostCategory category = categories.get(cost.getCostCategoryId());
        EquipmentCostDrilldownResponse.SourceLink sourceLink = sourceLink(
                cost,
                workOrderId,
                repairRequestId,
                contractorWorkId
        );

        return new EquipmentCostDrilldownResponse.Row(
                cost.getId(),
                cost.getAmount(),
                DEFAULT_CURRENCY,
                cost.getStatus(),
                category == null ? null : new EquipmentCostDrilldownResponse.CategoryRef(
                        category.getId(), category.getCode(), category.getName()),
                cost.getSourceType(),
                cost.getSourceId(),
                sourceDisplay(cost, workOrderId, repairRequestId, contractorWorkId, workOrders, repairRequests, contractorWorks),
                sourceLink,
                workOrderId,
                repairRequestId,
                contractorWorkId,
                cost.getCreatedAt() != null ? cost.getCreatedAt() : cost.getCostDate(),
                cost.getReviewedAt()
        );
    }

    private EquipmentCostDrilldownResponse.SourceLink sourceLink(ActualCost cost,
                                                                 UUID workOrderId,
                                                                 UUID repairRequestId,
                                                                 UUID contractorWorkId) {
        if (cost.getSourceType() == ActualCostSourceType.CONTRACTOR_WORK && contractorWorkId != null) {
            return new EquipmentCostDrilldownResponse.SourceLink("CONTRACTOR_WORK", contractorWorkId, "/contractors?workId=" + contractorWorkId);
        }
        if (cost.getSourceType() == ActualCostSourceType.REPAIR_REQUEST && repairRequestId != null) {
            return new EquipmentCostDrilldownResponse.SourceLink("REPAIR_REQUEST", repairRequestId, "/repair-requests/" + repairRequestId);
        }
        if ((cost.getSourceType() == ActualCostSourceType.WORK_ORDER
                || cost.getSourceType() == ActualCostSourceType.WORK_ORDER_MANUAL_WITH_REASON)
                && workOrderId != null) {
            return new EquipmentCostDrilldownResponse.SourceLink("WORK_ORDER", workOrderId, "/work-orders/" + workOrderId);
        }
        if (workOrderId != null) {
            return new EquipmentCostDrilldownResponse.SourceLink("WORK_ORDER", workOrderId, "/work-orders/" + workOrderId);
        }
        if (repairRequestId != null) {
            return new EquipmentCostDrilldownResponse.SourceLink("REPAIR_REQUEST", repairRequestId, "/repair-requests/" + repairRequestId);
        }
        if (contractorWorkId != null) {
            return new EquipmentCostDrilldownResponse.SourceLink("CONTRACTOR_WORK", contractorWorkId, "/contractors?workId=" + contractorWorkId);
        }
        if (cost.getSourceId() != null && cost.getSourceType() != null) {
            return new EquipmentCostDrilldownResponse.SourceLink(cost.getSourceType().name(), cost.getSourceId(), null);
        }
        return null;
    }

    private String sourceDisplay(ActualCost cost,
                                 UUID workOrderId,
                                 UUID repairRequestId,
                                 UUID contractorWorkId,
                                 Map<UUID, WorkOrder> workOrders,
                                 Map<UUID, RepairRequest> repairRequests,
                                 Map<UUID, ContractorWork> contractorWorks) {
        if (contractorWorkId != null && contractorWorks.containsKey(contractorWorkId)) {
            ContractorWork contractorWork = contractorWorks.get(contractorWorkId);
            return joinNonBlank("Contractor work", contractorWork.getDescription());
        }
        if (cost.getSourceType() == ActualCostSourceType.MATERIAL_ISSUE) {
            return workOrderId != null && workOrders.containsKey(workOrderId)
                    ? joinNonBlank("Material issue", joinNonBlank(workOrders.get(workOrderId).getNumber(), workOrders.get(workOrderId).getTitle()))
                    : sourceTypeLabel(cost.getSourceType());
        }
        if (cost.getSourceType() == ActualCostSourceType.LABOR_ENTRY) {
            return workOrderId != null && workOrders.containsKey(workOrderId)
                    ? joinNonBlank("Labor entry", joinNonBlank(workOrders.get(workOrderId).getNumber(), workOrders.get(workOrderId).getTitle()))
                    : sourceTypeLabel(cost.getSourceType());
        }
        if (workOrderId != null && workOrders.containsKey(workOrderId)) {
            WorkOrder workOrder = workOrders.get(workOrderId);
            return joinNonBlank(workOrder.getNumber(), workOrder.getTitle());
        }
        if (repairRequestId != null && repairRequests.containsKey(repairRequestId)) {
            RepairRequest repairRequest = repairRequests.get(repairRequestId);
            return joinNonBlank(repairRequest.getNumber(), repairRequest.getTitle());
        }
        return sourceTypeLabel(cost.getSourceType());
    }

    private UUID directWorkOrderId(ActualCost cost) {
        if (cost.getWorkOrderId() != null) {
            return cost.getWorkOrderId();
        }
        if ((cost.getSourceType() == ActualCostSourceType.WORK_ORDER
                || cost.getSourceType() == ActualCostSourceType.WORK_ORDER_MANUAL_WITH_REASON)
                && cost.getSourceId() != null) {
            return cost.getSourceId();
        }
        return null;
    }

    private UUID resolvedWorkOrderId(ActualCost cost, Map<UUID, ContractorWork> contractorWorks) {
        UUID workOrderId = directWorkOrderId(cost);
        if (workOrderId != null) {
            return workOrderId;
        }
        UUID contractorWorkId = directContractorWorkId(cost);
        ContractorWork contractorWork = contractorWorkId == null ? null : contractorWorks.get(contractorWorkId);
        return contractorWork == null ? null : contractorWork.getWorkOrderId();
    }

    private UUID directRepairRequestId(ActualCost cost) {
        if (cost.getRepairRequestId() != null) {
            return cost.getRepairRequestId();
        }
        if (cost.getSourceType() == ActualCostSourceType.REPAIR_REQUEST && cost.getSourceId() != null) {
            return cost.getSourceId();
        }
        return null;
    }

    private UUID directContractorWorkId(ActualCost cost) {
        if (cost.getContractorWorkId() != null) {
            return cost.getContractorWorkId();
        }
        if (cost.getSourceType() == ActualCostSourceType.CONTRACTOR_WORK && cost.getSourceId() != null) {
            return cost.getSourceId();
        }
        return null;
    }

    private List<EquipmentCostDrilldownResponse.Bucket> buckets(List<EquipmentCostDrilldownResponse.Row> rows,
                                                                Function<EquipmentCostDrilldownResponse.Row, String> keyFn,
                                                                Function<EquipmentCostDrilldownResponse.Row, String> labelFn) {
        Map<String, BucketAccumulator> grouped = new LinkedHashMap<>();
        for (EquipmentCostDrilldownResponse.Row row : rows) {
            String key = keyFn.apply(row);
            BucketAccumulator acc = grouped.computeIfAbsent(key, ignored -> new BucketAccumulator(key, labelFn.apply(row)));
            acc.add(row);
        }
        return grouped.values().stream()
                .map(BucketAccumulator::toBucket)
                .toList();
    }

    private double sumByStatus(Collection<EquipmentCostDrilldownResponse.Row> rows, ActualCostStatus status) {
        return rows.stream()
                .filter(row -> row.status() == status)
                .mapToDouble(EquipmentCostDrilldownResponse.Row::amount)
                .sum();
    }

    private double sumTotal(Collection<EquipmentCostDrilldownResponse.Row> rows) {
        return rows.stream()
                .mapToDouble(EquipmentCostDrilldownResponse.Row::amount)
                .sum();
    }

    private Instant costSortInstant(ActualCost cost) {
        return cost.getCostDate() != null ? cost.getCostDate() : cost.getCreatedAt();
    }

    private String displayCategory(EquipmentCostDrilldownResponse.CategoryRef category) {
        return joinNonBlank(category.code(), category.name());
    }

    private String sourceTypeLabel(ActualCostSourceType sourceType) {
        if (sourceType == null) {
            return "Unspecified";
        }
        return switch (sourceType) {
            case WORK_ORDER -> "Work order";
            case REPAIR_REQUEST -> "Repair request";
            case CONTRACTOR_WORK -> "Contractor work";
            case MATERIAL_ISSUE -> "Material issue";
            case LABOR_ENTRY -> "Labor entry";
            case PROCUREMENT_RECEIPT -> "Procurement receipt";
            case WORK_ORDER_MANUAL_WITH_REASON -> "Manual work order cost";
        };
    }

    private String joinNonBlank(String first, String second) {
        List<String> parts = new ArrayList<>();
        if (first != null && !first.isBlank()) {
            parts.add(first);
        }
        if (second != null && !second.isBlank()) {
            parts.add(second);
        }
        return parts.isEmpty() ? "-" : String.join(" - ", parts);
    }

    private static final class BucketAccumulator {
        private final String key;
        private final String label;
        private double approved;
        private double pending;
        private double rejected;
        private long count;

        private BucketAccumulator(String key, String label) {
            this.key = key;
            this.label = label;
        }

        private void add(EquipmentCostDrilldownResponse.Row row) {
            count++;
            if (row.status() == ActualCostStatus.APPROVED) {
                approved += row.amount();
            } else if (row.status() == ActualCostStatus.PENDING) {
                pending += row.amount();
            } else if (row.status() == ActualCostStatus.REJECTED) {
                rejected += row.amount();
            }
        }

        private EquipmentCostDrilldownResponse.Bucket toBucket() {
            return new EquipmentCostDrilldownResponse.Bucket(
                    key,
                    label,
                    approved + pending + rejected,
                    approved,
                    pending,
                    rejected,
                    count
            );
        }
    }
}

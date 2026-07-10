package com.toir.service.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.repairrequest.RepairRequestCostKind;
import com.toir.dto.repairrequest.RepairRequestCostRowDto;
import com.toir.dto.repairrequest.RepairRequestCostsSummaryDto;
import com.toir.entity.LaborEntry;
import com.toir.entity.SparePart;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.User;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.repository.AuditLogRepository;
import com.toir.repository.CostCategoryRepository;
import com.toir.repository.LaborEntryRepository;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.SparePartRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.contarctor.ContractorWorkRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.repair.RepairMaterialUsageRepository;
import com.toir.repository.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepairRequestInsightsService {

    private static final String DEFAULT_CURRENCY = "UZS";

    private final ActualCostRepository actualCostRepository;
    private final WorkOrderRepository workOrderRepository;
    private final CostCategoryRepository costCategoryRepository;
    private final LaborEntryRepository laborEntryRepository;
    private final RepairMaterialUsageRepository materialUsageRepository;
    private final SparePartRepository sparePartRepository;
    private final ContractorWorkRepository contractorWorkRepository;
    private final DefectRepository defectRepository;
    private final EquipmentMeterRepository equipmentMeterRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public RepairRequestCostsSummaryDto getCostsSummary(RepairRequest request) {
        List<ActualCost> costs = deduplicateCosts(actualCostRepository.findAllForRepairRequest(request.getId()));
        Map<UUID, WorkOrder> workOrders = loadWorkOrders(costs);
        CostEnrichment enrichment = loadCostEnrichment(costs, workOrders);

        List<RepairRequestCostRowDto> rows = costs.stream()
                .map(cost -> toCostRow(cost, enrichment))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(RepairRequestCostRowDto::costDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RepairRequestCostRowDto::id))
                .toList();

        double laborCost = sum(rows, RepairRequestCostKind.LABOR);
        double contractorCost = sum(rows, RepairRequestCostKind.CONTRACTOR);
        double materialCost = sum(rows, RepairRequestCostKind.MATERIAL);
        return new RepairRequestCostsSummaryDto(
                request.getId(),
                DEFAULT_CURRENCY,
                laborCost + contractorCost + materialCost,
                laborCost,
                contractorCost,
                materialCost,
                rows
        );
    }

    private List<ActualCost> deduplicateCosts(List<ActualCost> costs) {
        Map<UUID, ActualCost> unique = new LinkedHashMap<>();
        for (ActualCost cost : safeList(costs)) {
            if (cost != null && cost.getId() != null) {
                unique.putIfAbsent(cost.getId(), cost);
            }
        }
        return List.copyOf(unique.values());
    }

    private Map<UUID, WorkOrder> loadWorkOrders(List<ActualCost> costs) {
        List<UUID> ids = costs.stream()
                .map(ActualCost::getWorkOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return safeList(workOrderRepository.findAllByIdInAndIsDeletedFalse(ids)).stream()
                .collect(Collectors.toMap(WorkOrder::getId, Function.identity()));
    }

    private CostEnrichment loadCostEnrichment(List<ActualCost> costs, Map<UUID, WorkOrder> workOrders) {
        Map<UUID, CostCategory> categories = mapById(
                costCategoryRepository.findAllByIdInAndIsDeletedFalse(distinctIds(costs, ActualCost::getCostCategoryId)),
                CostCategory::getId
        );
        Map<UUID, LaborEntry> laborEntries = mapById(
                laborEntryRepository.findAllByIdInAndIsDeletedFalse(sourceIds(costs, ActualCostSourceType.LABOR_ENTRY)),
                LaborEntry::getId
        );
        Map<UUID, RepairMaterialUsage> materialUsages = mapById(
                materialUsageRepository.findAllByIdInAndIsDeletedFalse(sourceIds(costs, ActualCostSourceType.MATERIAL_ISSUE)),
                RepairMaterialUsage::getId
        );
        Map<UUID, ContractorWork> contractorWorks = mapById(
                contractorWorkRepository.findAllByIdInAndIsDeletedFalse(contractorWorkIds(costs)),
                ContractorWork::getId
        );
        Map<UUID, User> users = mapById(
                userRepository.findAllByIdInAndIsDeletedFalse(distinctIds(laborEntries.values(), LaborEntry::getUserId)),
                User::getId
        );
        Map<UUID, SparePart> spareParts = mapById(
                sparePartRepository.findAllByIdInAndIsDeletedFalse(
                        distinctIds(materialUsages.values(), RepairMaterialUsage::getSparePartId)),
                SparePart::getId
        );
        return new CostEnrichment(workOrders, categories, laborEntries, materialUsages, contractorWorks, users, spareParts);
    }

    private RepairRequestCostRowDto toCostRow(ActualCost cost, CostEnrichment enrichment) {
        RepairRequestCostKind kind = kindFor(cost, enrichment.categories());
        if (kind == null) {
            return null;
        }
        WorkOrder workOrder = cost.getWorkOrderId() == null ? null : enrichment.workOrders().get(cost.getWorkOrderId());
        return new RepairRequestCostRowDto(
                cost.getId(),
                kind,
                sourceLabel(cost, kind, enrichment),
                cost.getWorkOrderId(),
                workOrder == null ? null : workOrder.getNumber(),
                cost.getStatus(),
                cost.getAmount(),
                effectiveCostDate(cost)
        );
    }

    private RepairRequestCostKind kindFor(ActualCost cost, Map<UUID, CostCategory> categories) {
        ActualCostSourceType sourceType = cost.getSourceType();
        if (sourceType == ActualCostSourceType.LABOR_ENTRY) {
            return RepairRequestCostKind.LABOR;
        }
        if (sourceType == ActualCostSourceType.MATERIAL_ISSUE) {
            return RepairRequestCostKind.MATERIAL;
        }
        if (sourceType == ActualCostSourceType.CONTRACTOR_WORK
                || sourceType == ActualCostSourceType.COUNTERAGENT_WORK) {
            return RepairRequestCostKind.CONTRACTOR;
        }
        CostCategory category = cost.getCostCategoryId() == null ? null : categories.get(cost.getCostCategoryId());
        if (category == null || category.getCode() == null) {
            return null;
        }
        String code = category.getCode().trim().toUpperCase();
        if (code.equals("LABOR")) {
            return RepairRequestCostKind.LABOR;
        }
        if (code.equals("MATERIAL") || code.equals("MATERIALS") || code.equals("SPARE_PARTS")) {
            return RepairRequestCostKind.MATERIAL;
        }
        if (code.equals("CONTRACTOR") || code.equals("CTR") || code.equals("COUNTERAGENT")) {
            return RepairRequestCostKind.CONTRACTOR;
        }
        return null;
    }

    private String sourceLabel(ActualCost cost, RepairRequestCostKind kind, CostEnrichment enrichment) {
        if (cost.getSourceType() == ActualCostSourceType.LABOR_ENTRY && cost.getSourceId() != null) {
            LaborEntry entry = enrichment.laborEntries().get(cost.getSourceId());
            if (entry != null) {
                User user = entry.getUserId() == null ? null : enrichment.users().get(entry.getUserId());
                String actor = user != null && hasText(user.getFullName())
                        ? user.getFullName().trim()
                        : hasText(entry.getContractorName()) ? entry.getContractorName().trim() : "Labor";
                return "%s — %sh @ %s".formatted(actor, formatNumber(entry.getHours()), formatNumber(entry.getRate()));
            }
        }
        if (cost.getSourceType() == ActualCostSourceType.MATERIAL_ISSUE && cost.getSourceId() != null) {
            RepairMaterialUsage usage = enrichment.materialUsages().get(cost.getSourceId());
            if (usage != null) {
                SparePart sparePart = usage.getSparePartId() == null ? null : enrichment.spareParts().get(usage.getSparePartId());
                String label = sparePart == null
                        ? "Material"
                        : hasText(sparePart.getName()) ? sparePart.getName().trim() : sparePart.getCode();
                return "%s × %s".formatted(label, formatNumber(usage.getQuantity()));
            }
        }
        if ((cost.getSourceType() == ActualCostSourceType.CONTRACTOR_WORK
                || cost.getSourceType() == ActualCostSourceType.COUNTERAGENT_WORK)
                && cost.getSourceId() != null) {
            ContractorWork contractorWork = enrichment.contractorWorks().get(cost.getSourceId());
            if (contractorWork != null && hasText(contractorWork.getDescription())) {
                return contractorWork.getDescription().trim();
            }
        }
        if (cost.getNotes() != null && !cost.getNotes().isBlank()) {
            return cost.getNotes().trim();
        }
        CostCategory category = cost.getCostCategoryId() == null ? null : enrichment.categories().get(cost.getCostCategoryId());
        if (category != null && hasText(category.getName())) {
            return category.getName().trim();
        }
        WorkOrder workOrder = cost.getWorkOrderId() == null ? null : enrichment.workOrders().get(cost.getWorkOrderId());
        if (workOrder != null && hasText(workOrder.getNumber())) {
            return workOrder.getNumber().trim();
        }
        return kind.name();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String formatNumber(Double value) {
        return value == null ? "0" : formatNumber(value.doubleValue());
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private List<UUID> sourceIds(List<ActualCost> costs, ActualCostSourceType type) {
        return costs.stream()
                .filter(cost -> cost.getSourceType() == type)
                .map(ActualCost::getSourceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<UUID> contractorWorkIds(List<ActualCost> costs) {
        return costs.stream()
                .filter(cost -> cost.getSourceType() == ActualCostSourceType.CONTRACTOR_WORK
                        || cost.getSourceType() == ActualCostSourceType.COUNTERAGENT_WORK)
                .map(cost -> cost.getContractorWorkId() != null ? cost.getContractorWorkId() : cost.getSourceId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private <T> List<UUID> distinctIds(Collection<T> values, Function<T, UUID> idExtractor) {
        return safeList(values).stream()
                .map(idExtractor)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private <T> Map<UUID, T> mapById(Collection<T> values, Function<T, UUID> idExtractor) {
        return safeList(values).stream()
                .filter(Objects::nonNull)
                .filter(value -> idExtractor.apply(value) != null)
                .collect(Collectors.toMap(idExtractor, Function.identity(), (left, ignored) -> left));
    }

    private Instant effectiveCostDate(ActualCost cost) {
        return cost.getCostDate() != null ? cost.getCostDate() : cost.getCreatedAt();
    }

    private double sum(List<RepairRequestCostRowDto> rows, RepairRequestCostKind kind) {
        return rows.stream()
                .filter(row -> row.kind() == kind)
                .filter(row -> row.status() != ActualCostStatus.REJECTED)
                .mapToDouble(RepairRequestCostRowDto::amount)
                .sum();
    }

    private <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private record CostEnrichment(
            Map<UUID, WorkOrder> workOrders,
            Map<UUID, CostCategory> categories,
            Map<UUID, LaborEntry> laborEntries,
            Map<UUID, RepairMaterialUsage> materialUsages,
            Map<UUID, ContractorWork> contractorWorks,
            Map<UUID, User> users,
            Map<UUID, SparePart> spareParts
    ) {
    }
}

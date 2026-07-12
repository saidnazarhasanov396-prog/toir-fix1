package com.toir.service.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.toir.dto.repairrequest.RepairRequestCloseReadinessDto;
import com.toir.dto.repairrequest.RepairRequestCloseReadinessItemDto;
import com.toir.dto.repairrequest.RepairRequestCostKind;
import com.toir.dto.repairrequest.RepairRequestCostRowDto;
import com.toir.dto.repairrequest.RepairRequestCostsSummaryDto;
import com.toir.dto.repairrequest.RepairRequestTimelineEventDto;
import com.toir.dto.repairrequest.RepairRequestTimelineEventType;
import com.toir.entity.AuditLog;
import com.toir.entity.LaborEntry;
import com.toir.entity.SparePart;
import com.toir.entity.contractors.ContractorWork;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.MeterReading;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.projects.CostCategory;
import com.toir.entity.repair.RepairMaterialUsage;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.users.User;
import com.toir.enums.ActualCostSourceType;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.CloseReadinessGroupStatus;
import com.toir.enums.CloseReadinessSeverity;
import com.toir.enums.DefectStatus;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepairRequestInsightsService {

    private static final String DEFAULT_CURRENCY = "UZS";
    private static final Set<WorkOrderStatus> TERMINAL_WORK_ORDER_STATUSES = Set.of(
            WorkOrderStatus.COMPLETED,
            WorkOrderStatus.CLOSED,
            WorkOrderStatus.CANCELLED
    );
    private static final Set<DefectStatus> TERMINAL_DEFECT_STATUSES = Set.of(
            DefectStatus.RESOLVED,
            DefectStatus.CLOSED,
            DefectStatus.CANCELLED
    );

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
        if (costs.isEmpty()) {
            return new RepairRequestCostsSummaryDto(
                    request.getId(), DEFAULT_CURRENCY, 0, 0, 0, 0, List.of());
        }
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

    @Transactional(readOnly = true)
    public RepairRequestCloseReadinessDto getCloseReadiness(RepairRequest request) {
        Instant checkedAt = Instant.now();
        List<WorkOrder> workOrders = safeList(workOrderRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()));
        List<Defect> defects = safeList(defectRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()));
        List<EquipmentMeter> activeMeters = request.getEquipmentId() == null
                ? List.of()
                : safeList(equipmentMeterRepository
                .findAllByEquipmentIdAndActiveTrueAndIsDeletedFalse(request.getEquipmentId()));
        List<MeterReading> readings = safeList(meterReadingRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(request.getId()));

        List<RepairRequestCloseReadinessItemDto> blockers = new ArrayList<>();
        List<RepairRequestCloseReadinessItemDto> warnings = new ArrayList<>();
        Map<String, CloseReadinessGroupStatus> groups = readinessGroups();

        if (request.getStatus() != RequestStatus.COMPLETED) {
            addBlocker(blockers, groups,
                    "REQUEST_NOT_COMPLETED",
                    "Repair request must be completed before it can be closed",
                    "workOrders",
                    "overview");
        }
        if (workOrders.isEmpty()) {
            addBlocker(blockers, groups,
                    "NO_LINKED_WORK_ORDERS",
                    "No work order is linked to this repair request",
                    "workOrders",
                    "related");
        } else {
            long openWorkOrders = workOrders.stream()
                    .filter(workOrder -> workOrder.getStatus() == null
                            || !TERMINAL_WORK_ORDER_STATUSES.contains(workOrder.getStatus()))
                    .count();
            if (openWorkOrders > 0) {
                addBlocker(blockers, groups,
                        "OPEN_WORK_ORDERS",
                        "%d linked work order(s) are still open".formatted(openWorkOrders),
                        "workOrders",
                        "related");
            }
        }

        long openDefects = defects.stream()
                .filter(defect -> defect.getStatus() == null || !TERMINAL_DEFECT_STATUSES.contains(defect.getStatus()))
                .count();
        if (openDefects > 0) {
            addBlocker(blockers, groups,
                    "OPEN_DEFECTS",
                    "%d linked defect(s) are still open".formatted(openDefects),
                    "defects",
                    "related");
        }

        if (Boolean.TRUE.equals(request.getWarrantyActiveAtCreation()) && request.getWarrantyHandling() == null) {
            addWarning(warnings, groups,
                    "WARRANTY_DECISION_PENDING",
                    "Warranty decision has not been recorded",
                    "warranty",
                    "warranty");
        }

        Set<UUID> recordedMeterIds = readings.stream()
                .map(MeterReading::getMeterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        long missingMeters = activeMeters.stream()
                .map(EquipmentMeter::getId)
                .filter(Objects::nonNull)
                .filter(meterId -> !recordedMeterIds.contains(meterId))
                .count();
        if (missingMeters > 0) {
            addWarning(warnings, groups,
                    "METER_READINGS_MISSING",
                    "%d active equipment meter(s) have no repair-request reading".formatted(missingMeters),
                    "meterReadings",
                    "resources");
        }

        boolean overdue = request.getTargetCompletionAt() != null
                && request.getActualCompletionAt() == null
                && request.getTargetCompletionAt().isBefore(checkedAt);
        if (overdue) {
            addWarning(warnings, groups,
                    "TARGET_COMPLETION_OVERDUE",
                    "Target completion date has passed",
                    "sla",
                    "overview");
        }

        return new RepairRequestCloseReadinessDto(
                request.getId(),
                request.getStatus(),
                blockers.isEmpty(),
                checkedAt,
                overdue,
                false,
                blockers,
                warnings,
                groups
        );
    }

    @Transactional(readOnly = true)
    public List<RepairRequestTimelineEventDto> getTimeline(RepairRequest request) {
        List<RepairRequestTimelineEventDto> events = new ArrayList<>();
        Instant createdAt = request.getCreatedAt() != null ? request.getCreatedAt() : request.getDetectedAt();
        if (createdAt != null) {
            events.add(new RepairRequestTimelineEventDto(
                    syntheticEventId(request.getId(), "created"),
                    RepairRequestTimelineEventType.CREATED,
                    createdAt,
                    request.getReporterId(),
                    null,
                    null,
                    requestInitialStatus(request),
                    null,
                    null,
                    null
            ));
        }

        String previousStatus = requestInitialStatus(request);
        for (AuditLog audit : safeList(auditLogRepository
                .findRepairRequestTimelineAudits(request.getId().toString()))) {
            if (audit == null || audit.getAction() == com.toir.enums.AuditAction.CREATE) {
                continue;
            }
            String newStatus = snapshotStatus(audit.getCurrentSnapshot());
            RepairRequestTimelineEventType type = classifyAuditEvent(audit, newStatus, previousStatus);
            if (type != null && audit.getCreatedAt() != null) {
                events.add(new RepairRequestTimelineEventDto(
                        audit.getId() == null
                                ? syntheticEventId(request.getId(), "audit:" + audit.getCreatedAt() + ":" + type)
                                : audit.getId(),
                        type,
                        audit.getCreatedAt(),
                        audit.getUserId(),
                        null,
                        statusChanged(previousStatus, newStatus) ? previousStatus : null,
                        statusChanged(previousStatus, newStatus) ? newStatus : null,
                        audit.getMessage(),
                        null,
                        null
                ));
            }
            if (newStatus != null) {
                previousStatus = newStatus;
            }
        }

        for (Defect defect : safeList(defectRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))) {
            if (defect == null || defect.getId() == null || defect.getCreatedAt() == null) {
                continue;
            }
            events.add(new RepairRequestTimelineEventDto(
                    syntheticEventId(request.getId(), "defect:" + defect.getId()),
                    RepairRequestTimelineEventType.DEFECT_LINKED,
                    defect.getCreatedAt(),
                    null,
                    null,
                    null,
                    null,
                    joinNonBlank(defect.getCode(), defect.getTitle()),
                    "DEFECT",
                    defect.getId()
            ));
        }

        for (WorkOrder workOrder : safeList(workOrderRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByUpdatedAtDesc(request.getId()))) {
            if (workOrder == null || workOrder.getId() == null || workOrder.getCreatedAt() == null) {
                continue;
            }
            events.add(new RepairRequestTimelineEventDto(
                    syntheticEventId(request.getId(), "work-order:" + workOrder.getId()),
                    RepairRequestTimelineEventType.WORK_ORDER_LINKED,
                    workOrder.getCreatedAt(),
                    null,
                    null,
                    null,
                    null,
                    joinNonBlank(workOrder.getNumber(), workOrder.getTitle()),
                    "WORK_ORDER",
                    workOrder.getId()
            ));
        }

        List<MeterReading> readings = safeList(meterReadingRepository
                .findAllByRepairRequestIdAndIsDeletedFalseOrderByReadAtDesc(request.getId()));
        List<UUID> meterIds = readings.stream()
                .map(MeterReading::getMeterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, EquipmentMeter> meters = meterIds.isEmpty()
                ? Map.of()
                : mapById(equipmentMeterRepository.findAllByIdInAndIsDeletedFalse(meterIds), EquipmentMeter::getId);
        for (MeterReading reading : readings) {
            Instant occurredAt = reading == null
                    ? null
                    : reading.getReadAt() != null ? reading.getReadAt() : reading.getCreatedAt();
            if (reading == null || reading.getId() == null || occurredAt == null) {
                continue;
            }
            events.add(new RepairRequestTimelineEventDto(
                    syntheticEventId(request.getId(), "meter-reading:" + reading.getId()),
                    RepairRequestTimelineEventType.METER_READING,
                    occurredAt,
                    reading.getRecordedByUserId(),
                    null,
                    null,
                    null,
                    meterReadingMessage(reading, meters),
                    null,
                    null
            ));
        }

        Map<UUID, User> actors = loadTimelineActors(events);
        return deduplicateTimeline(events).stream()
                .map(event -> withActorName(event, actors))
                .sorted(Comparator
                        .comparing(RepairRequestTimelineEventDto::occurredAt)
                        .thenComparing(event -> event.id().toString()))
                .toList();
    }

    private String meterReadingMessage(MeterReading reading, Map<UUID, EquipmentMeter> meters) {
        EquipmentMeter meter = reading.getMeterId() == null ? null : meters.get(reading.getMeterId());
        if (meter == null || !hasText(meter.getName())) {
            return "Meter reading: " + formatNumber(reading.getValue());
        }
        String unit = hasText(meter.getUnit()) ? " " + meter.getUnit().trim() : "";
        return "%s: %s%s".formatted(meter.getName().trim(), formatNumber(reading.getValue()), unit);
    }

    private RepairRequestTimelineEventType classifyAuditEvent(AuditLog audit,
                                                               String newStatus,
                                                               String previousStatus) {
        String message = audit.getMessage() == null ? "" : audit.getMessage().toLowerCase(Locale.ROOT);
        if (audit.getAction() == com.toir.enums.AuditAction.CLOSE || "CLOSED".equals(newStatus)) {
            return RepairRequestTimelineEventType.CLOSED;
        }
        if (audit.getAction() == com.toir.enums.AuditAction.CANCEL || "REJECTED".equals(newStatus)) {
            return RepairRequestTimelineEventType.REJECTED;
        }
        if (message.contains("warranty decision") || message.contains("гарант")) {
            return RepairRequestTimelineEventType.WARRANTY_DECISION;
        }
        if (message.contains("clarification") || message.contains("уточнен")) {
            return RepairRequestTimelineEventType.CLARIFICATION_REQUESTED;
        }
        if (message.contains("assigned") || message.contains("назначен")) {
            return RepairRequestTimelineEventType.ASSIGNED;
        }
        return statusChanged(previousStatus, newStatus)
                ? RepairRequestTimelineEventType.STATUS_CHANGE
                : null;
    }

    private String snapshotStatus(String snapshot) {
        if (!hasText(snapshot)) {
            return null;
        }
        try {
            JsonNode statusNode = objectMapper.readTree(snapshot).path("status");
            return statusNode.isTextual() && hasText(statusNode.asText()) ? statusNode.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String requestInitialStatus(RepairRequest request) {
        return RequestStatus.OPEN.name();
    }

    private boolean statusChanged(String previousStatus, String newStatus) {
        return newStatus != null && !Objects.equals(previousStatus, newStatus);
    }

    private UUID syntheticEventId(UUID requestId, String discriminator) {
        return UUID.nameUUIDFromBytes(
                ("repair-request:" + requestId + ":" + discriminator).getBytes(StandardCharsets.UTF_8));
    }

    private String joinNonBlank(String first, String second) {
        if (hasText(first) && hasText(second)) {
            return first.trim() + " · " + second.trim();
        }
        if (hasText(first)) {
            return first.trim();
        }
        return hasText(second) ? second.trim() : null;
    }

    private Map<UUID, User> loadTimelineActors(List<RepairRequestTimelineEventDto> events) {
        List<UUID> actorIds = events.stream()
                .map(RepairRequestTimelineEventDto::actorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        return mapById(userRepository.findAllByIdInAndIsDeletedFalse(actorIds), User::getId);
    }

    private RepairRequestTimelineEventDto withActorName(RepairRequestTimelineEventDto event,
                                                        Map<UUID, User> actors) {
        User actor = event.actorId() == null ? null : actors.get(event.actorId());
        return new RepairRequestTimelineEventDto(
                event.id(),
                event.type(),
                event.occurredAt(),
                event.actorId(),
                actor == null ? null : actor.getFullName(),
                event.fromStatus(),
                event.toStatus(),
                event.message(),
                event.targetType(),
                event.targetId()
        );
    }

    private List<RepairRequestTimelineEventDto> deduplicateTimeline(List<RepairRequestTimelineEventDto> events) {
        Map<String, RepairRequestTimelineEventDto> unique = new LinkedHashMap<>();
        for (RepairRequestTimelineEventDto event : events) {
            String key = event.type() + "|" + event.occurredAt() + "|" + event.targetId() + "|" + event.toStatus();
            unique.putIfAbsent(key, event);
        }
        return List.copyOf(unique.values());
    }

    private Map<String, CloseReadinessGroupStatus> readinessGroups() {
        Map<String, CloseReadinessGroupStatus> groups = new LinkedHashMap<>();
        groups.put("workOrders", CloseReadinessGroupStatus.READY);
        groups.put("defects", CloseReadinessGroupStatus.READY);
        groups.put("warranty", CloseReadinessGroupStatus.READY);
        groups.put("meterReadings", CloseReadinessGroupStatus.READY);
        groups.put("sla", CloseReadinessGroupStatus.READY);
        return groups;
    }

    private void addBlocker(List<RepairRequestCloseReadinessItemDto> blockers,
                            Map<String, CloseReadinessGroupStatus> groups,
                            String code,
                            String message,
                            String group,
                            String targetTab) {
        blockers.add(new RepairRequestCloseReadinessItemDto(
                code, message, CloseReadinessSeverity.BLOCKING, group, targetTab));
        groups.put(group, CloseReadinessGroupStatus.BLOCKED);
    }

    private void addWarning(List<RepairRequestCloseReadinessItemDto> warnings,
                            Map<String, CloseReadinessGroupStatus> groups,
                            String code,
                            String message,
                            String group,
                            String targetTab) {
        warnings.add(new RepairRequestCloseReadinessItemDto(
                code, message, CloseReadinessSeverity.WARNING, group, targetTab));
        groups.compute(group, (ignored, status) -> status == CloseReadinessGroupStatus.BLOCKED
                ? CloseReadinessGroupStatus.BLOCKED
                : CloseReadinessGroupStatus.WARNING);
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
                cost.getAmount().doubleValue(),
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

    private String formatNumber(java.math.BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
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

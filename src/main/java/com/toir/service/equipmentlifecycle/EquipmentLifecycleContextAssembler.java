package com.toir.service.equipmentlifecycle;

import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextPolicy;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleSection;
import com.toir.entity.ConditionReading;
import com.toir.entity.PprTask;
import com.toir.entity.TechnicalDocument;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.equipment.EquipmentAttributeDefinition;
import com.toir.entity.equipment.EquipmentAttributeValue;
import com.toir.entity.equipment.EquipmentLocationHistory;
import com.toir.entity.equipment.EquipmentMeter;
import com.toir.entity.equipment.EquipmentNode;
import com.toir.entity.equipment.EquipmentPassport;
import com.toir.entity.equipment.EquipmentStatusHistory;
import com.toir.entity.equipment.MeterReading;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.MaintenanceDueEvent;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.projects.ActualCost;
import com.toir.entity.repair.RepairRequest;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.ActualCostStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.repository.ConditionReadingRepository;
import com.toir.repository.MeterReadingRepository;
import com.toir.repository.PprTaskRepository;
import com.toir.repository.TechnicalDocumentRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.actualCost.ActualCostRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentAttributeDefinitionRepository;
import com.toir.repository.equipment.EquipmentAttributeValueRepository;
import com.toir.repository.equipment.EquipmentLocationHistoryRepository;
import com.toir.repository.equipment.EquipmentMeterRepository;
import com.toir.repository.equipment.EquipmentNodeRepository;
import com.toir.repository.equipment.EquipmentPassportRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.equipment.EquipmentStatusHistoryRepository;
import com.toir.repository.inspection.EquipmentInspectionLifecycleProjection;
import com.toir.repository.inspection.EquipmentInspectionLifecycleRepository;
import com.toir.repository.maintenance.MaintenanceCompletionAnchorRepository;
import com.toir.repository.maintenance.MaintenanceDueEventRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.repository.sparepartlifecycle.SparePartInstallationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentLifecycleContextAssembler {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tashkent");
    private static final String CURRENCY = "UZS";

    private final EquipmentRepository equipmentRepository;
    private final EquipmentPassportRepository passportRepository;
    private final EquipmentNodeRepository nodeRepository;
    private final EquipmentAttributeValueRepository attributeValueRepository;
    private final EquipmentAttributeDefinitionRepository attributeDefinitionRepository;
    private final EquipmentStatusHistoryRepository statusHistoryRepository;
    private final EquipmentLocationHistoryRepository locationHistoryRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final EquipmentMeterRepository meterRepository;
    private final MaintenanceCompletionAnchorRepository completionAnchorRepository;
    private final WorkOrderRepository workOrderRepository;
    private final PprTaskRepository pprTaskRepository;
    private final MaintenanceDueEventRepository dueEventRepository;
    private final DefectRepository defectRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final EquipmentInspectionLifecycleRepository inspectionRepository;
    private final ConditionReadingRepository conditionReadingRepository;
    private final SparePartInstallationRepository installationRepository;
    private final TechnicalDocumentRepository documentRepository;
    private final ActualCostRepository actualCostRepository;
    private final EquipmentLifecycleCanonicalMapper canonicalMapper;
    private final EquipmentLifecycleFingerprintService fingerprintService;
    private final Clock clock;

    public EquipmentLifecycleContextAssembler(
            EquipmentRepository equipmentRepository,
            EquipmentPassportRepository passportRepository,
            EquipmentNodeRepository nodeRepository,
            EquipmentAttributeValueRepository attributeValueRepository,
            EquipmentAttributeDefinitionRepository attributeDefinitionRepository,
            EquipmentStatusHistoryRepository statusHistoryRepository,
            EquipmentLocationHistoryRepository locationHistoryRepository,
            MeterReadingRepository meterReadingRepository,
            EquipmentMeterRepository meterRepository,
            MaintenanceCompletionAnchorRepository completionAnchorRepository,
            WorkOrderRepository workOrderRepository,
            PprTaskRepository pprTaskRepository,
            MaintenanceDueEventRepository dueEventRepository,
            DefectRepository defectRepository,
            RepairRequestRepository repairRequestRepository,
            EquipmentInspectionLifecycleRepository inspectionRepository,
            ConditionReadingRepository conditionReadingRepository,
            SparePartInstallationRepository installationRepository,
            TechnicalDocumentRepository documentRepository,
            ActualCostRepository actualCostRepository,
            EquipmentLifecycleCanonicalMapper canonicalMapper,
            EquipmentLifecycleFingerprintService fingerprintService,
            @Qualifier("equipmentLifecycleClock") Clock clock
    ) {
        this.equipmentRepository = equipmentRepository;
        this.passportRepository = passportRepository;
        this.nodeRepository = nodeRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.attributeDefinitionRepository = attributeDefinitionRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.locationHistoryRepository = locationHistoryRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.meterRepository = meterRepository;
        this.completionAnchorRepository = completionAnchorRepository;
        this.workOrderRepository = workOrderRepository;
        this.pprTaskRepository = pprTaskRepository;
        this.dueEventRepository = dueEventRepository;
        this.defectRepository = defectRepository;
        this.repairRequestRepository = repairRequestRepository;
        this.inspectionRepository = inspectionRepository;
        this.conditionReadingRepository = conditionReadingRepository;
        this.installationRepository = installationRepository;
        this.documentRepository = documentRepository;
        this.actualCostRepository = actualCostRepository;
        this.canonicalMapper = canonicalMapper;
        this.fingerprintService = fingerprintService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EquipmentLifecycleContextV1 assemble(
            UUID equipmentId,
            Instant asOf,
            EquipmentLifecycleContextPolicy policy
    ) {
        requireRequest(equipmentId, asOf, policy);
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));
        Instant generatedAt = clock.instant();
        List<EquipmentLifecycleDataQuality.QualityIssue> issues = new ArrayList<>();
        List<String> missingCriticalFields = missingCriticalFields(equipment);
        issues.add(issue(
                "context",
                "SNAPSHOT_CONSISTENCY_LIMITED",
                EquipmentLifecycleDataQuality.IssueSeverity.INFO,
                "Context is assembled in one read-only transaction at the database default isolation level",
                List.of()));
        if (equipment.getUpdatedAt() != null && equipment.getUpdatedAt().isAfter(asOf)) {
            issues.add(issue(
                    "equipment",
                    "CURRENT_STATE_POSTDATES_AS_OF",
                    EquipmentLifecycleDataQuality.IssueSeverity.WARNING,
                    "Current Equipment row was updated after asOf; historical reconstruction is unavailable",
                    List.of(equipmentId)));
        }

        EquipmentPassport passport = passportRepository
                .findByEquipmentIdAndIsDeletedFalse(equipmentId)
                .orElse(null);
        EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.EquipmentCore>
                equipmentSection = equipmentSection(equipment, passport);
        EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.WarrantyInfo>
                warrantySection = warrantySection(equipment);
        EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.EnvironmentContext>
                environmentSection = environmentSection(equipment);

        List<EquipmentLifecycleContextV1.SourceWatermark> watermarks = new ArrayList<>();

        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.HierarchyNode>
                hierarchy = loadHierarchy(equipmentId, policy);
        addWatermark(watermarks, "equipment_nodes", hierarchy.metadata());

        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.TechnicalAttribute>
                attributes = loadAttributes(equipmentId, policy, issues);
        addWatermark(watermarks, "equipment_attribute_values", attributes.metadata());

        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.LifecycleEvent>
                lifecycle = loadLifecycle(equipmentId, asOf, policy);
        addWatermark(watermarks, "equipment_lifecycle_history", lifecycle.metadata());

        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.MeterReadingItem>
                meters = loadMeters(equipmentId, asOf, policy, issues);
        addWatermark(watermarks, "meter_readings", meters.metadata());

        WorkOrderLoad workOrderLoad = loadWorkOrderSource(equipmentId, asOf, policy);
        MaintenanceLoad maintenance = loadMaintenance(
                equipmentId, asOf, policy, workOrderLoad, issues);
        addWatermark(watermarks, "maintenance_completion_anchors+work_orders",
                maintenance.history().metadata());
        addWatermark(watermarks, "work_orders", workOrderLoad.section().metadata());

        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.PlannedMaintenanceItem>
                planned = loadPlanned(equipmentId, asOf, policy, workOrderLoad.source(), issues);
        addWatermark(watermarks, "planned_maintenance", planned.metadata());

        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.DefectItem>
                defects = loadDefects(equipmentId, asOf, policy, issues);
        addWatermark(watermarks, "defects", defects.metadata());

        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.RepairRequestItem>
                repairRequests = loadRepairRequests(equipmentId, asOf, policy);
        addWatermark(watermarks, "repair_requests", repairRequests.metadata());

        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.InspectionItem>
                inspections = loadInspections(equipmentId, asOf, policy);
        addWatermark(watermarks, "inspection_round_results", inspections.metadata());

        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.ConditionMeasurementItem>
                conditions = loadConditions(equipmentId, asOf, policy);
        addWatermark(watermarks, "condition_readings", conditions.metadata());

        ComponentLoad components = loadComponents(equipmentId, asOf, policy);
        addWatermark(watermarks, "spare_part_installations", components.installed().metadata());
        addWatermark(watermarks, "spare_part_installations_replacement_view",
                components.replacements().metadata());

        EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.CostSummary>
                costs = loadCosts(equipmentId, asOf, policy, issues);
        addWatermark(watermarks, "actual_costs", costs.metadata());

        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.DocumentMetadata>
                documents = loadDocuments(equipmentId, asOf, policy);
        addWatermark(watermarks, "technical_documents", documents.metadata());

        addTruncationIssue(issues, "hierarchy", hierarchy.metadata());
        addTruncationIssue(issues, "technicalAttributes", attributes.metadata());
        addTruncationIssue(issues, "lifecycleHistory", lifecycle.metadata());
        addTruncationIssue(issues, "meterHistory", meters.metadata());
        addTruncationIssue(issues, "maintenanceHistory", maintenance.history().metadata());
        addTruncationIssue(issues, "plannedMaintenance", planned.metadata());
        addTruncationIssue(issues, "workOrders", workOrderLoad.section().metadata());
        addTruncationIssue(issues, "defects", defects.metadata());
        addTruncationIssue(issues, "repairRequests", repairRequests.metadata());
        addTruncationIssue(issues, "inspections", inspections.metadata());
        addTruncationIssue(issues, "conditionMeasurements", conditions.metadata());
        addTruncationIssue(issues, "installedComponents", components.installed().metadata());
        addTruncationIssue(
                issues,
                "componentReplacementHistory",
                components.replacements().metadata());
        addTruncationIssue(issues, "costSummary", costs.metadata());
        addTruncationIssue(issues, "documentMetadata", documents.metadata());

        EquipmentLifecycleContextV1 context = new EquipmentLifecycleContextV1(
                EquipmentLifecycleContextV1.SCHEMA_VERSION,
                generatedAt,
                asOf,
                null,
                new EquipmentLifecycleContextV1.ConsistencyMetadata(
                        "TRANSACTION_READ_ONLY_DEFAULT_ISOLATION", false),
                equipmentSection,
                hierarchy,
                attributes,
                lifecycle,
                meters,
                maintenance.summary(),
                maintenance.history(),
                planned,
                workOrderLoad.section(),
                defects,
                repairRequests,
                inspections,
                conditions,
                components.installed(),
                components.replacements(),
                warrantySection,
                costs,
                environmentSection,
                documents,
                new EquipmentLifecycleDataQuality(issues, missingCriticalFields),
                watermarks);
        return context.withFingerprint(fingerprintService.fingerprint(context, policy));
    }

    private EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.HierarchyNode>
            loadHierarchy(UUID equipmentId, EquipmentLifecycleContextPolicy policy) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.HIERARCHY;
        if (!policy.includes(section)) {
            return omittedItems();
        }
        int limit = policy.requiredLimit(section);
        Bounded<EquipmentNode> bounded = bounded(
                nodeRepository.findLifecycleNodes(equipmentId, limit + 1), limit);
        List<EquipmentLifecycleContextV1.HierarchyNode> items = bounded.items().stream()
                .map(node -> new EquipmentLifecycleContextV1.HierarchyNode(
                        node.getId(), node.getParentId(), bounded(node.getCode()),
                        bounded(node.getName()), name(node.getNodeType())))
                .toList();
        return itemsSection("equipment_nodes", items, bounded.truncated(),
                bounded.items().stream().map(EquipmentNode::getUpdatedAt).toList());
    }

    private EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.TechnicalAttribute>
            loadAttributes(
                    UUID equipmentId,
                    EquipmentLifecycleContextPolicy policy,
                    List<EquipmentLifecycleDataQuality.QualityIssue> issues
            ) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.TECHNICAL_ATTRIBUTES;
        if (!policy.includes(section)) {
            return omittedItems();
        }
        int limit = policy.requiredLimit(section);
        Bounded<EquipmentAttributeValue> bounded = bounded(
                attributeValueRepository.findLifecycleValues(equipmentId, limit + 1), limit);
        Map<UUID, EquipmentAttributeDefinition> definitions = new HashMap<>();
        List<UUID> definitionIds = bounded.items().stream()
                .map(EquipmentAttributeValue::getAttributeDefinitionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!definitionIds.isEmpty()) {
            attributeDefinitionRepository.findAllByIdInAndIsDeletedFalse(definitionIds)
                    .forEach(definition -> definitions.put(definition.getId(), definition));
        }
        List<EquipmentLifecycleContextV1.TechnicalAttribute> items = new ArrayList<>();
        for (EquipmentAttributeValue value : bounded.items()) {
            EquipmentAttributeDefinition definition =
                    definitions.get(value.getAttributeDefinitionId());
            if (definition == null) {
                issues.add(issue(
                        "technicalAttributes",
                        "MISSING_ATTRIBUTE_DEFINITION",
                        EquipmentLifecycleDataQuality.IssueSeverity.WARNING,
                        "Attribute value references a missing or deleted definition",
                        List.of(value.getId())));
            }
            if (value.getValueJson() != null && !value.getValueJson().isBlank()) {
                issues.add(issue(
                        "technicalAttributes",
                        "UNMAPPED_JSON_ATTRIBUTE",
                        EquipmentLifecycleDataQuality.IssueSeverity.INFO,
                        "JSON attribute value is intentionally excluded from v1",
                        List.of(value.getId())));
            }
            items.add(new EquipmentLifecycleContextV1.TechnicalAttribute(
                    value.getId(),
                    value.getAttributeDefinitionId(),
                    definition == null ? null : bounded(definition.getKey()),
                    definition == null ? null : bounded(definition.getLabel()),
                    definition == null ? null : name(definition.getDataType()),
                    definition == null ? null : bounded(definition.getUnit()),
                    bounded(value.getValueText()),
                    value.getValueNumber(),
                    value.getValueDate(),
                    value.getValueBoolean(),
                    bounded(value.getValueOption())));
        }
        return itemsSection("equipment_attribute_values", items, bounded.truncated(),
                bounded.items().stream().map(EquipmentAttributeValue::getUpdatedAt).toList());
    }

    private EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.LifecycleEvent>
            loadLifecycle(
                    UUID equipmentId,
                    Instant asOf,
                    EquipmentLifecycleContextPolicy policy
            ) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.LIFECYCLE_HISTORY;
        if (!policy.includes(section)) {
            return omittedItems();
        }
        int limit = policy.requiredLimit(section);
        List<EquipmentStatusHistory> statuses = statusHistoryRepository.findLifecycleHistory(
                equipmentId, policy.historyStart(), asOf, limit + 1);
        List<EquipmentLocationHistory> locations = locationHistoryRepository.findLifecycleHistory(
                equipmentId, policy.historyStart(), asOf, limit + 1);
        List<EquipmentLifecycleContextV1.LifecycleEvent> combined = new ArrayList<>();
        statuses.forEach(history -> combined.add(new EquipmentLifecycleContextV1.LifecycleEvent(
                history.getId(),
                "EQUIPMENT_STATUS_HISTORY",
                history.getChangedAt(),
                history.getChangedAt(),
                singleton("status", name(history.getFromStatus())),
                singleton("status", name(history.getToStatus())),
                name(history.getSource()),
                bounded(history.getRelatedEntityType()),
                history.getRelatedEntityId())));
        locations.forEach(history -> combined.add(new EquipmentLifecycleContextV1.LifecycleEvent(
                history.getId(),
                "EQUIPMENT_LOCATION_HISTORY",
                history.getChangedAt(),
                history.getChangedAt(),
                locationValues(
                        history.getFromLocationType(),
                        history.getFromDepartmentId(),
                        history.getFromWarehouseId(),
                        history.getFromOutsideReason()),
                locationValues(
                        history.getToLocationType(),
                        history.getToDepartmentId(),
                        history.getToWarehouseId(),
                        history.getToOutsideReason()),
                "EQUIPMENT_LOCATION",
                null,
                null)));
        combined.sort(Comparator.comparing(
                        EquipmentLifecycleContextV1.LifecycleEvent::eventTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EquipmentLifecycleContextV1.LifecycleEvent::sourceType)
                .thenComparing(EquipmentLifecycleContextV1.LifecycleEvent::sourceId));
        Bounded<EquipmentLifecycleContextV1.LifecycleEvent> bounded =
                bounded(combined, limit);
        boolean truncated = bounded.truncated()
                || statuses.size() > limit
                || locations.size() > limit;
        return itemsSection("equipment_status_history+equipment_location_history",
                bounded.items(), truncated,
                bounded.items().stream().map(EquipmentLifecycleContextV1.LifecycleEvent::eventTime).toList());
    }

    private EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.MeterReadingItem>
            loadMeters(
                    UUID equipmentId,
                    Instant asOf,
                    EquipmentLifecycleContextPolicy policy,
                    List<EquipmentLifecycleDataQuality.QualityIssue> issues
            ) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.METER_HISTORY;
        if (!policy.includes(section)) {
            return omittedItems();
        }
        int limit = policy.requiredLimit(section);
        Bounded<MeterReading> bounded = bounded(
                meterReadingRepository.findLifecycleReadings(
                        equipmentId, policy.historyStart(), asOf, limit + 1),
                limit);
        Map<UUID, EquipmentMeter> meters = new HashMap<>();
        List<UUID> meterIds = bounded.items().stream()
                .map(MeterReading::getMeterId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!meterIds.isEmpty()) {
            meterRepository.findAllByIdInAndIsDeletedFalse(meterIds)
                    .forEach(meter -> meters.put(meter.getId(), meter));
        }
        List<EquipmentLifecycleContextV1.MeterReadingItem> items = new ArrayList<>();
        for (MeterReading reading : bounded.items()) {
            EquipmentMeter meter = meters.get(reading.getMeterId());
            if (meter == null || meter.getUnit() == null || meter.getUnit().isBlank()) {
                issues.add(issue(
                        "meterHistory",
                        "UNKNOWN_UNIT",
                        EquipmentLifecycleDataQuality.IssueSeverity.WARNING,
                        "Meter reading has no reliable unit",
                        List.of(reading.getId())));
            }
            items.add(new EquipmentLifecycleContextV1.MeterReadingItem(
                    reading.getId(),
                    reading.getMeterId(),
                    meter == null ? null : name(meter.getMeterType()),
                    meter == null ? null : bounded(meter.getUnit()),
                    reading.getValue(),
                    reading.getDelta(),
                    reading.getReadAt(),
                    name(reading.getSource()),
                    name(reading.getReadingContext()),
                    reading.getRepairRequestId(),
                    reading.getWorkOrderId(),
                    reading.getDefectId()));
        }
        return itemsSection("meter_readings", items, bounded.truncated(),
                bounded.items().stream().map(MeterReading::getReadAt).toList());
    }

    private WorkOrderLoad loadWorkOrderSource(
            UUID equipmentId,
            Instant asOf,
            EquipmentLifecycleContextPolicy policy
    ) {
        boolean needed = policy.includes(EquipmentLifecycleSection.WORK_ORDERS)
                || policy.includes(EquipmentLifecycleSection.MAINTENANCE_HISTORY)
                || policy.includes(EquipmentLifecycleSection.PLANNED_MAINTENANCE);
        if (!needed) {
            return new WorkOrderLoad(List.of(), false, omittedItems());
        }
        int limit = maxIncludedLimit(
                policy,
                EquipmentLifecycleSection.WORK_ORDERS,
                EquipmentLifecycleSection.MAINTENANCE_HISTORY,
                EquipmentLifecycleSection.PLANNED_MAINTENANCE);
        Bounded<WorkOrder> bounded = bounded(
                workOrderRepository.findLifecycleWorkOrders(
                        equipmentId, policy.historyStart(), asOf, limit + 1),
                limit);
        if (!policy.includes(EquipmentLifecycleSection.WORK_ORDERS)) {
            return new WorkOrderLoad(bounded.items(), bounded.truncated(), omittedItems());
        }
        int sectionLimit = policy.requiredLimit(EquipmentLifecycleSection.WORK_ORDERS);
        Bounded<WorkOrder> forSection = bounded(bounded.items(), sectionLimit);
        List<EquipmentLifecycleContextV1.WorkOrderItem> items = forSection.items().stream()
                .map(workOrder -> new EquipmentLifecycleContextV1.WorkOrderItem(
                        workOrder.getId(),
                        bounded(workOrder.getNumber()),
                        name(workOrder.getStatus()),
                        name(workOrder.getType()),
                        name(workOrder.getWorkType()),
                        name(workOrder.getPriority()),
                        workOrder.getEquipmentNodeId(),
                        workOrder.getRepairRequestId(),
                        workOrder.getDefectId(),
                        workOrder.getPprTaskId(),
                        workOrder.getMaintenanceDueEventId(),
                        workOrder.getStartPlannedAt(),
                        workOrder.getEndPlannedAt(),
                        workOrder.getStartedAt(),
                        workOrder.getCompletedAt()))
                .toList();
        boolean truncated = bounded.truncated() || forSection.truncated();
        return new WorkOrderLoad(
                bounded.items(),
                bounded.truncated(),
                itemsSection("work_orders", items, truncated,
                        forSection.items().stream()
                                .map(this::workOrderEventTime)
                                .toList()));
    }

    private MaintenanceLoad loadMaintenance(
            UUID equipmentId,
            Instant asOf,
            EquipmentLifecycleContextPolicy policy,
            WorkOrderLoad workOrders,
            List<EquipmentLifecycleDataQuality.QualityIssue> issues
    ) {
        if (!policy.includes(EquipmentLifecycleSection.MAINTENANCE_HISTORY)) {
            return new MaintenanceLoad(omittedValue(), omittedItems());
        }
        int limit = policy.requiredLimit(EquipmentLifecycleSection.MAINTENANCE_HISTORY);
        Bounded<MaintenanceCompletionAnchor> anchors = bounded(
                completionAnchorRepository.findLifecycleAnchors(
                        equipmentId, policy.historyStart(), asOf, limit + 1),
                limit);
        List<UUID> candidateWorkOrderIds = workOrders.source().stream()
                .map(WorkOrder::getId)
                .filter(Objects::nonNull)
                .toList();
        Set<UUID> anchoredWorkOrderIds = candidateWorkOrderIds.isEmpty()
                ? Set.of()
                : Set.copyOf(completionAnchorRepository.findAnchoredWorkOrderIds(
                        equipmentId, candidateWorkOrderIds, asOf));
        List<EquipmentLifecycleContextV1.MaintenanceEvent> mapped =
                canonicalMapper.maintenanceEvents(
                        anchors.items(), workOrders.source(), anchoredWorkOrderIds, issues);
        Bounded<EquipmentLifecycleContextV1.MaintenanceEvent> events = bounded(mapped, limit);
        boolean truncated = anchors.truncated()
                || workOrders.sourceTruncated()
                || workOrders.source().size() > limit
                || events.truncated();
        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.MaintenanceEvent>
                history = itemsSection(
                        "maintenance_completion_anchors+work_orders",
                        events.items(),
                        truncated,
                        events.items().stream()
                                .map(EquipmentLifecycleContextV1.MaintenanceEvent::actualCompletionAt)
                                .toList());
        int terminalWorkOrders = (int) workOrders.source().stream()
                .filter(workOrder -> workOrder.getStatus() == WorkOrderStatus.COMPLETED
                        || workOrder.getStatus() == WorkOrderStatus.CLOSED)
                .count();
        Instant lastCompletedAt = events.items().stream()
                .map(EquipmentLifecycleContextV1.MaintenanceEvent::actualCompletionAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        EquipmentLifecycleContextV1.MaintenanceSummary value =
                new EquipmentLifecycleContextV1.MaintenanceSummary(
                        events.items().size(),
                        lastCompletedAt,
                        terminalWorkOrders,
                        null);
        return new MaintenanceLoad(
                new EquipmentLifecycleContextV1.ValueSection<>(
                        history.metadata(), value),
                history);
    }

    private EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.PlannedMaintenanceItem>
            loadPlanned(
                    UUID equipmentId,
                    Instant asOf,
                    EquipmentLifecycleContextPolicy policy,
                    List<WorkOrder> workOrders,
                    List<EquipmentLifecycleDataQuality.QualityIssue> issues
            ) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.PLANNED_MAINTENANCE;
        if (!policy.includes(section)) {
            return omittedItems();
        }
        int limit = policy.requiredLimit(section);
        LocalDateTime historyStart = LocalDateTime.ofInstant(policy.historyStart(), BUSINESS_ZONE);
        LocalDateTime planningEnd = LocalDateTime.ofInstant(policy.planningEnd(asOf), BUSINESS_ZONE);
        List<PprTask> tasks = pprTaskRepository.findLifecycleTasks(
                equipmentId, historyStart, planningEnd, limit + 1);
        List<MaintenanceDueEvent> dueEvents = dueEventRepository.findLifecycleDueEvents(
                equipmentId, asOf, policy.planningEnd(asOf), limit + 1);
        Map<UUID, UUID> workOrderByTask = new HashMap<>();
        workOrders.stream()
                .filter(workOrder -> workOrder.getPprTaskId() != null)
                .sorted(Comparator.comparing(WorkOrder::getId))
                .forEach(workOrder -> workOrderByTask.putIfAbsent(
                        workOrder.getPprTaskId(), workOrder.getId()));
        List<EquipmentLifecycleContextV1.PlannedMaintenanceItem> combined = new ArrayList<>();
        tasks.forEach(task -> combined.add(new EquipmentLifecycleContextV1.PlannedMaintenanceItem(
                task.getId(),
                "PPR_TASK",
                task.getSourceCalculationItemId(),
                task.getId(),
                task.getMaintenanceDueEventId(),
                workOrderByTask.get(task.getId()),
                name(task.getStatus()),
                toInstant(task.getScheduledStart()),
                toInstant(task.getScheduledEnd()),
                toInstant(task.getDueDate()),
                null,
                null,
                null,
                null)));
        for (MaintenanceDueEvent dueEvent : dueEvents) {
            String unit = meterUnit(dueEvent);
            if ((dueEvent.getMeterCurrentValue() != null || dueEvent.getMeterRemaining() != null)
                    && unit == null) {
                issues.add(issue(
                        "plannedMaintenance",
                        "UNKNOWN_UNIT",
                        EquipmentLifecycleDataQuality.IssueSeverity.WARNING,
                        "Maintenance due event has meter values but no code-proven unit",
                        List.of(dueEvent.getId())));
            }
            combined.add(new EquipmentLifecycleContextV1.PlannedMaintenanceItem(
                    dueEvent.getId(),
                    "MAINTENANCE_DUE_EVENT",
                    null,
                    dueEvent.getCreatedTaskId(),
                    dueEvent.getId(),
                    dueEvent.getCreatedWorkOrderId(),
                    name(dueEvent.getDueStatus()),
                    null,
                    null,
                    dueEvent.getDueAt(),
                    name(dueEvent.getMeterType()),
                    unit,
                    dueEvent.getMeterCurrentValue(),
                    dueEvent.getMeterRemaining()));
        }
        combined.sort(Comparator.comparing(
                        this::plannedEventTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EquipmentLifecycleContextV1.PlannedMaintenanceItem::sourceType)
                .thenComparing(EquipmentLifecycleContextV1.PlannedMaintenanceItem::sourceId));
        Bounded<EquipmentLifecycleContextV1.PlannedMaintenanceItem> bounded =
                bounded(combined, limit);
        boolean truncated = bounded.truncated()
                || tasks.size() > limit
                || dueEvents.size() > limit;
        return itemsSection(
                "ppr_tasks+maintenance_due_events",
                bounded.items(),
                truncated,
                bounded.items().stream().map(this::plannedEventTime).toList());
    }

    private EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.DefectItem>
            loadDefects(
                    UUID equipmentId,
                    Instant asOf,
                    EquipmentLifecycleContextPolicy policy,
                    List<EquipmentLifecycleDataQuality.QualityIssue> issues
            ) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.DEFECTS;
        if (!policy.includes(section)) {
            return omittedItems();
        }
        int limit = policy.requiredLimit(section);
        Bounded<Defect> bounded = bounded(
                defectRepository.findLifecycleDefects(
                        equipmentId, policy.historyStart(), asOf, limit + 1),
                limit);
        List<EquipmentLifecycleContextV1.DefectItem> items =
                canonicalMapper.defects(bounded.items(), issues);
        return itemsSection("defects", items, bounded.truncated(),
                bounded.items().stream().map(Defect::getDetectedAt).toList());
    }

    private EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.RepairRequestItem>
            loadRepairRequests(
                    UUID equipmentId,
                    Instant asOf,
                    EquipmentLifecycleContextPolicy policy
            ) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.REPAIR_REQUESTS;
        if (!policy.includes(section)) {
            return omittedItems();
        }
        int limit = policy.requiredLimit(section);
        Bounded<RepairRequest> bounded = bounded(
                repairRequestRepository.findLifecycleRequests(
                        equipmentId, policy.historyStart(), asOf, limit + 1),
                limit);
        List<EquipmentLifecycleContextV1.RepairRequestItem> items = bounded.items().stream()
                .map(request -> new EquipmentLifecycleContextV1.RepairRequestItem(
                        request.getId(),
                        request.getDepartmentId(),
                        request.getLocationId(),
                        name(request.getPriority()),
                        name(request.getCriticality()),
                        name(request.getStatus()),
                        name(request.getSource()),
                        request.getDetectedAt(),
                        request.getReactedAt(),
                        request.getTargetCompletionAt(),
                        request.getActualCompletionAt(),
                        request.getWarrantyActiveAtCreation(),
                        name(request.getWarrantyHandling())))
                .toList();
        return itemsSection("repair_requests", items, bounded.truncated(),
                bounded.items().stream().map(RepairRequest::getDetectedAt).toList());
    }

    private EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.InspectionItem>
            loadInspections(
                    UUID equipmentId,
                    Instant asOf,
                    EquipmentLifecycleContextPolicy policy
            ) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.INSPECTIONS;
        if (!policy.includes(section)) {
            return omittedItems();
        }
        int limit = policy.requiredLimit(section);
        Bounded<EquipmentInspectionLifecycleProjection> bounded = bounded(
                inspectionRepository.findLifecycleInspections(
                        equipmentId, policy.historyStart(), asOf, limit + 1),
                limit);
        List<EquipmentLifecycleContextV1.InspectionItem> items = bounded.items().stream()
                .map(result -> new EquipmentLifecycleContextV1.InspectionItem(
                        result.getSourceId(),
                        result.getRoundId(),
                        result.getCheckpointId(),
                        result.getRoundStatus(),
                        result.getResultStatus(),
                        result.getStartedAt(),
                        result.getCompletedAt(),
                        result.getMeasuredValue(),
                        bounded(result.getMeasuredUnit()),
                        result.getExpectedMin(),
                        result.getExpectedMax(),
                        bounded(result.getExpectedUnit()),
                        result.getDefectId()))
                .toList();
        return itemsSection("inspection_round_results", items, bounded.truncated(),
                bounded.items().stream()
                        .map(item -> firstNonNull(
                                item.getCompletedAt(), item.getStartedAt(), item.getSourceUpdatedAt()))
                        .toList());
    }

    private EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.ConditionMeasurementItem>
            loadConditions(
                    UUID equipmentId,
                    Instant asOf,
                    EquipmentLifecycleContextPolicy policy
            ) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.CONDITION_MEASUREMENTS;
        if (!policy.includes(section)) {
            return omittedItems();
        }
        int limit = policy.requiredLimit(section);
        Bounded<ConditionReading> bounded = bounded(
                conditionReadingRepository.findLifecycleReadings(
                        equipmentId, policy.historyStart(), asOf, limit + 1),
                limit);
        List<EquipmentLifecycleContextV1.ConditionMeasurementItem> items = bounded.items().stream()
                .map(reading -> new EquipmentLifecycleContextV1.ConditionMeasurementItem(
                        reading.getId(),
                        name(reading.getParameter()),
                        reading.getValue(),
                        bounded(reading.getUnit()),
                        reading.getRecordedAt(),
                        reading.getWarnHigh(),
                        reading.getAlarmHigh(),
                        reading.getWarnLow(),
                        reading.getAlarmLow(),
                        bounded(reading.getSeverity())))
                .toList();
        return itemsSection("condition_readings", items, bounded.truncated(),
                bounded.items().stream().map(ConditionReading::getRecordedAt).toList());
    }

    private ComponentLoad loadComponents(
            UUID equipmentId,
            Instant asOf,
            EquipmentLifecycleContextPolicy policy
    ) {
        boolean installedIncluded =
                policy.includes(EquipmentLifecycleSection.INSTALLED_COMPONENTS);
        boolean replacementsIncluded =
                policy.includes(EquipmentLifecycleSection.COMPONENT_REPLACEMENT_HISTORY);
        if (!installedIncluded && !replacementsIncluded) {
            return new ComponentLoad(omittedItems(), omittedItems());
        }
        int sourceLimit = maxIncludedLimit(
                policy,
                EquipmentLifecycleSection.INSTALLED_COMPONENTS,
                EquipmentLifecycleSection.COMPONENT_REPLACEMENT_HISTORY);
        Bounded<SparePartInstallation> source = bounded(
                installationRepository.findLifecycleInstallations(
                        equipmentId, policy.historyStart(), asOf, sourceLimit + 1),
                sourceLimit);
        EquipmentLifecycleCanonicalMapper.ComponentViews views =
                canonicalMapper.components(source.items());
        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.InstalledComponentItem>
                installed = omittedItems();
        if (installedIncluded) {
            int limit = policy.requiredLimit(EquipmentLifecycleSection.INSTALLED_COMPONENTS);
            Bounded<EquipmentLifecycleContextV1.InstalledComponentItem> bounded =
                    bounded(views.installed(), limit);
            installed = itemsSection(
                    "spare_part_installations",
                    bounded.items(),
                    source.truncated() || bounded.truncated(),
                    bounded.items().stream()
                            .map(EquipmentLifecycleContextV1.InstalledComponentItem::installedAt)
                            .toList());
        }
        EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.ComponentReplacementItem>
                replacements = omittedItems();
        if (replacementsIncluded) {
            int limit = policy.requiredLimit(
                    EquipmentLifecycleSection.COMPONENT_REPLACEMENT_HISTORY);
            Bounded<EquipmentLifecycleContextV1.ComponentReplacementItem> bounded =
                    bounded(views.replacements(), limit);
            replacements = itemsSection(
                    "spare_part_installations",
                    bounded.items(),
                    source.truncated() || bounded.truncated(),
                    bounded.items().stream()
                            .map(EquipmentLifecycleContextV1.ComponentReplacementItem::removedAt)
                            .toList());
        }
        return new ComponentLoad(installed, replacements);
    }

    private EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.CostSummary>
            loadCosts(
                    UUID equipmentId,
                    Instant asOf,
                    EquipmentLifecycleContextPolicy policy,
                    List<EquipmentLifecycleDataQuality.QualityIssue> issues
            ) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.COST_SUMMARY;
        if (!policy.includes(section)) {
            return omittedValue();
        }
        int limit = policy.requiredLimit(section);
        Bounded<ActualCost> bounded = bounded(
                actualCostRepository.findLifecycleCosts(
                        equipmentId, policy.historyStart(), asOf, limit + 1),
                limit);
        EnumMap<ActualCostStatus, BigDecimal> byStatus =
                new EnumMap<>(ActualCostStatus.class);
        BigDecimal total = BigDecimal.ZERO;
        for (ActualCost cost : bounded.items()) {
            BigDecimal amount = cost.getAmount();
            if (amount == null) {
                continue;
            }
            total = total.add(amount);
            if (cost.getStatus() != null) {
                byStatus.merge(cost.getStatus(), amount, BigDecimal::add);
            }
        }
        if (bounded.truncated()) {
            issues.add(issue(
                    "costSummary",
                    "PARTIAL_AGGREGATE_DUE_TO_BOUND",
                    EquipmentLifecycleDataQuality.IssueSeverity.WARNING,
                    "Cost totals cover returned direct-link rows only because the section limit was reached",
                    bounded.items().stream().map(ActualCost::getId).toList()));
        }
        EquipmentLifecycleContextV1.CostSummary summary =
                new EquipmentLifecycleContextV1.CostSummary(
                        money(total),
                        money(byStatus.getOrDefault(ActualCostStatus.APPROVED, BigDecimal.ZERO)),
                        money(byStatus.getOrDefault(ActualCostStatus.PENDING, BigDecimal.ZERO)),
                        money(byStatus.getOrDefault(ActualCostStatus.REJECTED, BigDecimal.ZERO)),
                        true);
        EquipmentLifecycleContextV1.SectionMetadata metadata = metadata(
                "actual_costs",
                bounded.items().size(),
                bounded.truncated(),
                bounded.items().stream().map(ActualCost::getCostDate).toList(),
                EquipmentLifecycleDataQuality.Availability.DERIVABLE);
        return new EquipmentLifecycleContextV1.ValueSection<>(metadata, summary);
    }

    private EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.DocumentMetadata>
            loadDocuments(
                    UUID equipmentId,
                    Instant asOf,
                    EquipmentLifecycleContextPolicy policy
            ) {
        EquipmentLifecycleSection section = EquipmentLifecycleSection.DOCUMENT_METADATA;
        if (!policy.includes(section)) {
            return omittedItems();
        }
        int limit = policy.requiredLimit(section);
        Bounded<TechnicalDocument> bounded = bounded(
                documentRepository.findLifecycleDocuments(equipmentId, asOf, limit + 1),
                limit);
        List<EquipmentLifecycleContextV1.DocumentMetadata> items = bounded.items().stream()
                .map(document -> new EquipmentLifecycleContextV1.DocumentMetadata(
                        document.getId(),
                        "TECHNICAL_DOCUMENT",
                        document.getEquipmentNodeId(),
                        name(document.getType()),
                        bounded(document.getTitle()),
                        bounded(document.getRevision()),
                        document.getDocumentDate(),
                        true))
                .toList();
        EquipmentLifecycleContextV1.SectionMetadata metadata = metadata(
                "technical_documents",
                items.size(),
                bounded.truncated(),
                bounded.items().stream().map(TechnicalDocument::getUpdatedAt).toList(),
                items.isEmpty()
                        ? EquipmentLifecycleDataQuality.Availability.AVAILABLE_BUT_OPTIONAL
                        : EquipmentLifecycleDataQuality.Availability.REQUIRES_DOCUMENT_PARSING);
        return new EquipmentLifecycleContextV1.ItemsSection<>(metadata, items);
    }

    private EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.EquipmentCore>
            equipmentSection(Equipment equipment, EquipmentPassport passport) {
        EquipmentLifecycleContextV1.EquipmentCore core =
                new EquipmentLifecycleContextV1.EquipmentCore(
                        equipment.getId(),
                        bounded(equipment.getCode()),
                        bounded(equipment.getName()),
                        bounded(equipment.getInventoryNumber()),
                        bounded(equipment.getTechnicalNumber()),
                        bounded(equipment.getSerialNumber()),
                        bounded(equipment.getModel()),
                        equipment.getProducedYear(),
                        equipment.getEquipmentTypeId(),
                        equipment.getDepartmentId(),
                        equipment.getLocationId(),
                        name(equipment.getCurrentLocationType()),
                        equipment.getCurrentWarehouseId(),
                        equipment.getResponsibleDepartmentId(),
                        equipment.getParentId(),
                        equipment.getCriticalityClassId(),
                        bounded(equipment.getManufacturer()),
                        name(equipment.getStatus()),
                        name(equipment.getCategory()),
                        equipment.getCommissionedAt(),
                        equipment.getArrivalDate(),
                        equipment.getOperationStartDate(),
                        equipment.getExpectedLifetimeMonths(),
                        equipment.getExpectedLifetimeYears(),
                        equipment.getExpectedLifetimeHours(),
                        name(equipment.getLifetimeCounterType()),
                        equipment.getLifetimeMeterId(),
                        equipment.getLifetimeLimitValue(),
                        passport(passport));
        List<Instant> times = new ArrayList<>();
        times.add(equipment.getUpdatedAt());
        if (passport != null) {
            times.add(passport.getUpdatedAt());
        }
        EquipmentLifecycleContextV1.SectionMetadata metadata = metadata(
                "equipment+equipment_passports",
                1,
                false,
                times,
                EquipmentLifecycleDataQuality.Availability.AVAILABLE_AND_POPULATED);
        return new EquipmentLifecycleContextV1.ValueSection<>(metadata, core);
    }

    private EquipmentLifecycleContextV1.Passport passport(EquipmentPassport passport) {
        if (passport == null) {
            return null;
        }
        return new EquipmentLifecycleContextV1.Passport(
                bounded(passport.getPassportNumber()),
                bounded(passport.getFactoryNumber()),
                bounded(passport.getManufacturerSerial()),
                measurement(passport.getPowerKw(), "kW"),
                measurement(passport.getVoltageV(), "V"),
                measurement(passport.getPressureBar(), "bar"),
                passport.getInstallDate(),
                passport.getLastInspectionDate());
    }

    private EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.WarrantyInfo>
            warrantySection(Equipment equipment) {
        EquipmentLifecycleContextV1.WarrantyInfo value =
                new EquipmentLifecycleContextV1.WarrantyInfo(
                        equipment.getHasWarranty(),
                        equipment.getWarrantyStartDate(),
                        equipment.getWarrantyEndDate(),
                        equipment.getWarrantyCounteragentId(),
                        equipment.getProcurementRequestId(),
                        equipment.getProcurementRequestLineId());
        return new EquipmentLifecycleContextV1.ValueSection<>(
                metadata(
                        "equipment",
                        1,
                        false,
                        List.of(equipment.getUpdatedAt()),
                        EquipmentLifecycleDataQuality.Availability.AVAILABLE_AND_POPULATED),
                value);
    }

    private EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.EnvironmentContext>
            environmentSection(Equipment equipment) {
        EquipmentLifecycleContextV1.EnvironmentContext value =
                new EquipmentLifecycleContextV1.EnvironmentContext(
                        equipment.getDepartmentId(),
                        equipment.getResponsibleDepartmentId(),
                        equipment.getLocationId(),
                        name(equipment.getCurrentLocationType()),
                        equipment.getCurrentWarehouseId(),
                        name(equipment.getOutsideReason()),
                        equipment.getOutsideStartedDate(),
                        equipment.getOutsideExpectedReturnDate());
        return new EquipmentLifecycleContextV1.ValueSection<>(
                metadata(
                        "equipment",
                        1,
                        false,
                        List.of(equipment.getUpdatedAt()),
                        EquipmentLifecycleDataQuality.Availability.AVAILABLE_AND_POPULATED),
                value);
    }

    private <T> EquipmentLifecycleContextV1.ItemsSection<T> itemsSection(
            String source,
            List<T> items,
            boolean truncated,
            List<Instant> times
    ) {
        EquipmentLifecycleDataQuality.Availability availability = items.isEmpty()
                ? EquipmentLifecycleDataQuality.Availability.AVAILABLE_BUT_OPTIONAL
                : EquipmentLifecycleDataQuality.Availability.AVAILABLE_AND_POPULATED;
        return new EquipmentLifecycleContextV1.ItemsSection<>(
                metadata(source, items.size(), truncated, times, availability),
                items);
    }

    private EquipmentLifecycleContextV1.SectionMetadata metadata(
            String source,
            int returnedCount,
            boolean truncated,
            List<Instant> times,
            EquipmentLifecycleDataQuality.Availability availability
    ) {
        List<Instant> present = times == null
                ? List.of()
                : times.stream().filter(Objects::nonNull).sorted().toList();
        Instant start = present.isEmpty() ? null : present.get(0);
        Instant end = present.isEmpty() ? null : present.get(present.size() - 1);
        return EquipmentLifecycleContextV1.SectionMetadata.available(
                availability,
                List.of(source),
                returnedCount,
                truncated,
                start,
                end,
                end);
    }

    private void addWatermark(
            List<EquipmentLifecycleContextV1.SourceWatermark> watermarks,
            String source,
            EquipmentLifecycleContextV1.SectionMetadata metadata
    ) {
        if (metadata == null
                || metadata.availability()
                == EquipmentLifecycleDataQuality.Availability.OUT_OF_SCOPE_FOR_V1) {
            return;
        }
        watermarks.add(new EquipmentLifecycleContextV1.SourceWatermark(
                source,
                metadata.maxSourceTime(),
                metadata.returnedCount(),
                metadata.truncated(),
                metadata.coverageStart(),
                metadata.coverageEnd(),
                metadata.reliability()));
    }

    private void addTruncationIssue(
            List<EquipmentLifecycleDataQuality.QualityIssue> issues,
            String section,
            EquipmentLifecycleContextV1.SectionMetadata metadata
    ) {
        if (metadata != null && metadata.truncated()) {
            issues.add(issue(
                    section,
                    "SECTION_TRUNCATED",
                    EquipmentLifecycleDataQuality.IssueSeverity.WARNING,
                    "The section reached its caller-supplied row bound",
                    List.of()));
        }
    }

    private EquipmentLifecycleContextV1.SectionMetadata omittedMetadata() {
        return EquipmentLifecycleContextV1.SectionMetadata.unavailable(
                EquipmentLifecycleDataQuality.Availability.OUT_OF_SCOPE_FOR_V1,
                EquipmentLifecycleDataQuality.SourceReliability.OUT_OF_SCOPE,
                List.of());
    }

    private <T> EquipmentLifecycleContextV1.ItemsSection<T> omittedItems() {
        return new EquipmentLifecycleContextV1.ItemsSection<>(omittedMetadata(), List.of());
    }

    private <T> EquipmentLifecycleContextV1.ValueSection<T> omittedValue() {
        return new EquipmentLifecycleContextV1.ValueSection<>(omittedMetadata(), null);
    }

    private List<String> missingCriticalFields(Equipment equipment) {
        List<String> missing = new ArrayList<>();
        addMissing(missing, "equipment.code", equipment.getCode());
        addMissing(missing, "equipment.name", equipment.getName());
        addMissing(missing, "equipment.inventoryNumber", equipment.getInventoryNumber());
        addMissing(missing, "equipment.equipmentTypeId", equipment.getEquipmentTypeId());
        addMissing(missing, "equipment.status", equipment.getStatus());
        return List.copyOf(missing);
    }

    private void addMissing(List<String> missing, String field, Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            missing.add(field);
        }
    }

    private Map<String, String> singleton(String key, String value) {
        if (value == null) {
            return Map.of();
        }
        return Map.of(key, value);
    }

    private Map<String, String> locationValues(
            Enum<?> type,
            UUID departmentId,
            UUID warehouseId,
            Enum<?> outsideReason
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "locationType", name(type));
        put(values, "departmentId", text(departmentId));
        put(values, "warehouseId", text(warehouseId));
        put(values, "outsideReason", name(outsideReason));
        return Map.copyOf(values);
    }

    private void put(Map<String, String> values, String key, String value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String bounded(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.substring(0, Math.min(512, normalized.length()));
    }

    private EquipmentLifecycleContextV1.MeasurementValue measurement(
            Double value,
            String unit
    ) {
        return value == null
                ? null
                : new EquipmentLifecycleContextV1.MeasurementValue(value, unit);
    }

    private EquipmentLifecycleContextV1.MoneyAmount money(BigDecimal amount) {
        return new EquipmentLifecycleContextV1.MoneyAmount(amount, CURRENCY);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toInstant();
    }

    private String meterUnit(MaintenanceDueEvent dueEvent) {
        if (dueEvent.getMeterType() == null) {
            return null;
        }
        return switch (dueEvent.getMeterType().name()) {
            case "ENGINE_HOURS" -> "h";
            case "MILEAGE_KM" -> "km";
            case "CYCLES" -> "cycle";
            default -> null;
        };
    }

    private Instant workOrderEventTime(WorkOrder workOrder) {
        return firstNonNull(
                workOrder.getCompletedAt(),
                workOrder.getStartedAt(),
                workOrder.getStartPlannedAt(),
                workOrder.getCreatedAt());
    }

    private Instant plannedEventTime(
            EquipmentLifecycleContextV1.PlannedMaintenanceItem item
    ) {
        return firstNonNull(item.dueAt(), item.scheduledStart(), item.scheduledEnd());
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private int maxIncludedLimit(
            EquipmentLifecycleContextPolicy policy,
            EquipmentLifecycleSection... sections
    ) {
        int result = 0;
        for (EquipmentLifecycleSection section : sections) {
            if (policy.includes(section)) {
                result = Math.max(result, policy.requiredLimit(section));
            }
        }
        if (result <= 0) {
            throw new IllegalArgumentException("At least one bounded section must be included");
        }
        return result;
    }

    private <T> Bounded<T> bounded(List<T> values, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<T> source = values == null ? List.of() : values;
        boolean truncated = source.size() > limit;
        return new Bounded<>(
                List.copyOf(source.subList(0, Math.min(source.size(), limit))),
                truncated);
    }

    private EquipmentLifecycleDataQuality.QualityIssue issue(
            String section,
            String code,
            EquipmentLifecycleDataQuality.IssueSeverity severity,
            String summary,
            List<UUID> sourceIds
    ) {
        return new EquipmentLifecycleDataQuality.QualityIssue(
                section, code, severity, summary, sourceIds);
    }

    private void requireRequest(
            UUID equipmentId,
            Instant asOf,
            EquipmentLifecycleContextPolicy policy
    ) {
        if (equipmentId == null || asOf == null || policy == null) {
            throw new IllegalArgumentException("equipmentId, asOf and policy are required");
        }
        if (policy.historyStart().isAfter(asOf)) {
            throw new IllegalArgumentException("historyStart must not be after asOf");
        }
    }

    private record Bounded<T>(List<T> items, boolean truncated) {}

    private record WorkOrderLoad(
            List<WorkOrder> source,
            boolean sourceTruncated,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.WorkOrderItem>
                    section
    ) {}

    private record MaintenanceLoad(
            EquipmentLifecycleContextV1.ValueSection<EquipmentLifecycleContextV1.MaintenanceSummary>
                    summary,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.MaintenanceEvent>
                    history
    ) {}

    private record ComponentLoad(
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.InstalledComponentItem>
                    installed,
            EquipmentLifecycleContextV1.ItemsSection<EquipmentLifecycleContextV1.ComponentReplacementItem>
                    replacements
    ) {}
}

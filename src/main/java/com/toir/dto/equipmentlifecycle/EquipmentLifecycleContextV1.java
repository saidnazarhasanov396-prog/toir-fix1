package com.toir.dto.equipmentlifecycle;

import com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality.Availability;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality.SourceReliability;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EquipmentLifecycleContextV1(
        String schemaVersion,
        Instant generatedAt,
        Instant asOf,
        String contextFingerprint,
        ConsistencyMetadata consistency,
        ValueSection<EquipmentCore> equipment,
        ItemsSection<HierarchyNode> hierarchy,
        ItemsSection<TechnicalAttribute> technicalAttributes,
        ItemsSection<LifecycleEvent> lifecycleHistory,
        ItemsSection<MeterReadingItem> meterHistory,
        ValueSection<MaintenanceSummary> maintenanceSummary,
        ItemsSection<MaintenanceEvent> maintenanceHistory,
        ItemsSection<PlannedMaintenanceItem> plannedMaintenance,
        ItemsSection<WorkOrderItem> workOrders,
        ItemsSection<DefectItem> defects,
        ItemsSection<RepairRequestItem> repairRequests,
        ItemsSection<InspectionItem> inspections,
        ItemsSection<ConditionMeasurementItem> conditionMeasurements,
        ItemsSection<InstalledComponentItem> installedComponents,
        ItemsSection<ComponentReplacementItem> componentReplacementHistory,
        ValueSection<WarrantyInfo> warranty,
        ValueSection<CostSummary> costSummary,
        ValueSection<EnvironmentContext> environmentContext,
        ItemsSection<DocumentMetadata> documentMetadata,
        EquipmentLifecycleDataQuality dataQuality,
        List<SourceWatermark> sourceWatermarks
) {
    public static final String SCHEMA_VERSION = "1.0";

    public EquipmentLifecycleContextV1 {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be " + SCHEMA_VERSION);
        }
        sourceWatermarks = sourceWatermarks == null ? List.of() : List.copyOf(sourceWatermarks);
        dataQuality = dataQuality == null ? EquipmentLifecycleDataQuality.empty() : dataQuality;
    }

    public EquipmentLifecycleContextV1 withFingerprint(String fingerprint) {
        return new EquipmentLifecycleContextV1(
                schemaVersion, generatedAt, asOf, fingerprint, consistency, equipment,
                hierarchy, technicalAttributes, lifecycleHistory, meterHistory,
                maintenanceSummary, maintenanceHistory, plannedMaintenance, workOrders,
                defects, repairRequests, inspections, conditionMeasurements,
                installedComponents, componentReplacementHistory, warranty, costSummary,
                environmentContext, documentMetadata, dataQuality, sourceWatermarks);
    }

    public static EquipmentLifecycleContextV1 empty(
            Instant generatedAt,
            Instant asOf,
            ConsistencyMetadata consistency,
            ValueSection<EquipmentCore> equipment,
            ItemsSection<MeterReadingItem> meterHistory
    ) {
        SectionMetadata omitted = SectionMetadata.unavailable(
                Availability.OUT_OF_SCOPE_FOR_V1,
                SourceReliability.OUT_OF_SCOPE,
                List.of());
        return new EquipmentLifecycleContextV1(
                SCHEMA_VERSION, generatedAt, asOf, null, consistency, equipment,
                new ItemsSection<>(omitted, List.of()),
                new ItemsSection<>(omitted, List.of()),
                new ItemsSection<>(omitted, List.of()),
                meterHistory,
                new ValueSection<>(omitted, null),
                new ItemsSection<>(omitted, List.of()),
                new ItemsSection<>(omitted, List.of()),
                new ItemsSection<>(omitted, List.of()),
                new ItemsSection<>(omitted, List.of()),
                new ItemsSection<>(omitted, List.of()),
                new ItemsSection<>(omitted, List.of()),
                new ItemsSection<>(omitted, List.of()),
                new ItemsSection<>(omitted, List.of()),
                new ItemsSection<>(omitted, List.of()),
                new ValueSection<>(omitted, null),
                new ValueSection<>(omitted, null),
                new ValueSection<>(omitted, null),
                new ItemsSection<>(omitted, List.of()),
                EquipmentLifecycleDataQuality.empty(),
                List.of());
    }

    public record ConsistencyMetadata(String level, boolean historicalSnapshot) {}

    public record SectionMetadata(
            Availability availability,
            SourceReliability reliability,
            int returnedCount,
            boolean truncated,
            Instant coverageStart,
            Instant coverageEnd,
            Instant maxSourceTime,
            List<String> sources
    ) {
        public SectionMetadata {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }

        public static SectionMetadata available(
                Availability availability,
                List<String> sources,
                int returnedCount,
                boolean truncated,
                Instant coverageStart,
                Instant coverageEnd,
                Instant maxSourceTime
        ) {
            SourceReliability reliability = switch (availability) {
                case AVAILABLE_AND_POPULATED -> SourceReliability.RELIABLE;
                case AVAILABLE_BUT_OPTIONAL -> SourceReliability.OPTIONAL;
                case AVAILABLE_BUT_UNRELIABLE -> SourceReliability.UNRELIABLE;
                case DERIVABLE -> SourceReliability.DERIVED;
                case MISSING -> SourceReliability.MISSING;
                case REQUIRES_DOCUMENT_PARSING -> SourceReliability.PARSER_REQUIRED;
                case OUT_OF_SCOPE_FOR_V1 -> SourceReliability.OUT_OF_SCOPE;
            };
            return new SectionMetadata(
                    availability, reliability, returnedCount, truncated,
                    coverageStart, coverageEnd, maxSourceTime, sources);
        }

        public static SectionMetadata unavailable(
                Availability availability,
                SourceReliability reliability,
                List<String> sources
        ) {
            return new SectionMetadata(
                    availability, reliability, 0, false,
                    null, null, null, sources);
        }
    }

    public record ValueSection<T>(SectionMetadata metadata, T value) {}

    public record ItemsSection<T>(SectionMetadata metadata, List<T> items) {
        public ItemsSection {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record SourceWatermark(
            String source,
            Instant maxSourceTime,
            int returnedCount,
            boolean truncated,
            Instant coverageStart,
            Instant coverageEnd,
            SourceReliability reliability
    ) {}

    public record EquipmentCore(
            UUID id,
            String code,
            String name,
            String inventoryNumber,
            String technicalNumber,
            String serialNumber,
            String model,
            Integer producedYear,
            UUID equipmentTypeId,
            UUID departmentId,
            UUID locationId,
            String currentLocationType,
            UUID currentWarehouseId,
            UUID responsibleDepartmentId,
            UUID parentId,
            UUID criticalityClassId,
            String manufacturer,
            String status,
            String category,
            LocalDate commissionedAt,
            LocalDate arrivalDate,
            LocalDate operationStartDate,
            Integer expectedLifetimeMonths,
            Integer expectedLifetimeYears,
            Long expectedLifetimeHours,
            String lifetimeCounterType,
            UUID lifetimeMeterId,
            Double lifetimeLimitValue,
            Passport passport
    ) {}

    public record Passport(
            String passportNumber,
            String factoryNumber,
            String manufacturerSerial,
            MeasurementValue power,
            MeasurementValue voltage,
            MeasurementValue pressure,
            LocalDate installDate,
            LocalDate lastInspectionDate
    ) {}

    public record MeasurementValue(Double value, String unit) {}

    public record HierarchyNode(
            UUID sourceId,
            UUID parentId,
            String code,
            String name,
            String nodeType
    ) {}

    public record TechnicalAttribute(
            UUID sourceId,
            UUID definitionId,
            String key,
            String label,
            String dataType,
            String unit,
            String textValue,
            Double numberValue,
            LocalDate dateValue,
            Boolean booleanValue,
            String optionValue
    ) {}

    public record LifecycleEvent(
            UUID sourceId,
            String sourceType,
            Instant eventTime,
            Instant effectiveTime,
            Map<String, String> previousValues,
            Map<String, String> newValues,
            String source,
            String relatedEntityType,
            UUID relatedEntityId
    ) {
        public LifecycleEvent {
            previousValues = previousValues == null ? Map.of() : Map.copyOf(previousValues);
            newValues = newValues == null ? Map.of() : Map.copyOf(newValues);
        }
    }

    public record MeterReadingItem(
            UUID sourceId,
            UUID meterId,
            String meterType,
            String unit,
            Double value,
            Double delta,
            Instant readAt,
            String source,
            String readingContext,
            UUID repairRequestId,
            UUID workOrderId,
            UUID defectId
    ) {}

    public record MaintenanceSummary(
            Integer canonicalCompletionCount,
            Instant lastCompletedAt,
            Integer completedWorkOrderCount,
            Integer overduePlannedMaintenanceCount
    ) {}

    public record MaintenanceEvent(
            UUID sourceId,
            String sourceType,
            Instant actualCompletionAt,
            String workOrderStatus,
            UUID workOrderId,
            UUID pprTaskId,
            UUID maintenanceDueEventId,
            UUID regulationId,
            UUID equipmentMaintenanceRuleId,
            Instant plannedDueAt
    ) {}

    public record PlannedMaintenanceItem(
            UUID sourceId,
            String sourceType,
            UUID sourceCalculationItemId,
            UUID pprTaskId,
            UUID maintenanceDueEventId,
            UUID linkedWorkOrderId,
            String status,
            Instant scheduledStart,
            Instant scheduledEnd,
            Instant dueAt,
            String meterType,
            String meterUnit,
            Double meterCurrentValue,
            Double meterRemaining
    ) {}

    public record WorkOrderItem(
            UUID sourceId,
            String number,
            String status,
            String type,
            String workType,
            String priority,
            UUID equipmentNodeId,
            UUID repairRequestId,
            UUID defectId,
            UUID pprTaskId,
            UUID maintenanceDueEventId,
            Instant startPlannedAt,
            Instant endPlannedAt,
            Instant startedAt,
            Instant completedAt
    ) {}

    public record DefectItem(
            UUID sourceId,
            UUID equipmentNodeId,
            UUID repairRequestId,
            String category,
            String severity,
            String failureReason,
            String rootCause,
            String status,
            Instant detectedAt,
            Instant resolvedAt,
            Integer derivedRepeatedFailureCount,
            SourceReliability recurrenceReliability
    ) {}

    public record RepairRequestItem(
            UUID sourceId,
            UUID departmentId,
            UUID locationId,
            String priority,
            String criticality,
            String status,
            String source,
            Instant detectedAt,
            Instant reactedAt,
            Instant targetCompletionAt,
            Instant actualCompletionAt,
            Boolean warrantyActiveAtCreation,
            String warrantyHandling
    ) {}

    public record InspectionItem(
            UUID sourceId,
            UUID roundId,
            UUID checkpointId,
            String roundStatus,
            String resultStatus,
            Instant startedAt,
            Instant completedAt,
            Double measuredValue,
            String measuredUnit,
            Double expectedMin,
            Double expectedMax,
            String expectedUnit,
            UUID defectId
    ) {}

    public record ConditionMeasurementItem(
            UUID sourceId,
            String parameter,
            Double value,
            String unit,
            Instant recordedAt,
            Double warnHigh,
            Double alarmHigh,
            Double warnLow,
            Double alarmLow,
            String severity
    ) {}

    public record InstalledComponentItem(
            UUID sourceId,
            UUID equipmentNodeId,
            String slotCode,
            UUID sparePartId,
            BigDecimal quantity,
            String status,
            Instant installedAt,
            UUID installWorkOrderId,
            UUID sourceMaterialUsageId,
            UUID appliedLifeRuleId,
            Integer appliedRuleRevision,
            String lifecycleEvaluationState,
            Instant nextCalendarDueAt
    ) {}

    public record ComponentReplacementItem(
            UUID sourceId,
            UUID sparePartId,
            Instant installedAt,
            Instant removedAt,
            UUID installWorkOrderId,
            UUID removeWorkOrderId,
            String removalDisposition,
            UUID replacesInstallationId,
            UUID replacedByInstallationId,
            UUID replacementCorrelationId
    ) {}

    public record WarrantyInfo(
            Boolean hasWarranty,
            LocalDate warrantyStartDate,
            LocalDate warrantyEndDate,
            UUID warrantyCounteragentId,
            UUID procurementRequestId,
            UUID procurementRequestLineId
    ) {}

    public record MoneyAmount(BigDecimal amount, String currency) {}

    public record CostSummary(
            MoneyAmount total,
            MoneyAmount approved,
            MoneyAmount pending,
            MoneyAmount rejected,
            boolean directLinksOnly
    ) {}

    public record EnvironmentContext(
            UUID departmentId,
            UUID responsibleDepartmentId,
            UUID locationId,
            String locationType,
            UUID warehouseId,
            String outsideReason,
            LocalDate outsideStartedDate,
            LocalDate outsideExpectedReturnDate
    ) {}

    public record DocumentMetadata(
            UUID sourceId,
            String sourceType,
            UUID equipmentNodeId,
            String documentType,
            String title,
            String revision,
            LocalDate documentDate,
            boolean requiresParsing
    ) {}
}

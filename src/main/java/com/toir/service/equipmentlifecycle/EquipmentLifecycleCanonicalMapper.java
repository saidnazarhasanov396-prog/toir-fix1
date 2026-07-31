package com.toir.service.equipmentlifecycle;

import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality;
import com.toir.entity.defects.Defect;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class EquipmentLifecycleCanonicalMapper {

    private static final Comparator<EquipmentLifecycleContextV1.MaintenanceEvent> MAINTENANCE_ORDER =
            Comparator.comparing(
                            EquipmentLifecycleContextV1.MaintenanceEvent::actualCompletionAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(EquipmentLifecycleContextV1.MaintenanceEvent::sourceType)
                    .thenComparing(EquipmentLifecycleContextV1.MaintenanceEvent::sourceId);

    public List<EquipmentLifecycleContextV1.MaintenanceEvent> maintenanceEvents(
            List<MaintenanceCompletionAnchor> anchors,
            List<WorkOrder> workOrders,
            List<EquipmentLifecycleDataQuality.QualityIssue> issues
    ) {
        return maintenanceEvents(anchors, workOrders, Set.of(), issues);
    }

    public List<EquipmentLifecycleContextV1.MaintenanceEvent> maintenanceEvents(
            List<MaintenanceCompletionAnchor> anchors,
            List<WorkOrder> workOrders,
            Set<UUID> knownAnchoredWorkOrderIds,
            List<EquipmentLifecycleDataQuality.QualityIssue> issues
    ) {
        List<EquipmentLifecycleContextV1.MaintenanceEvent> events = new ArrayList<>();
        Set<UUID> anchoredWorkOrders = new HashSet<>(
                knownAnchoredWorkOrderIds == null ? Set.of() : knownAnchoredWorkOrderIds);
        for (MaintenanceCompletionAnchor anchor : safe(anchors)) {
            if (anchor.getWorkOrderId() != null) {
                anchoredWorkOrders.add(anchor.getWorkOrderId());
            }
            events.add(new EquipmentLifecycleContextV1.MaintenanceEvent(
                    anchor.getId(),
                    "MAINTENANCE_COMPLETION_ANCHOR",
                    anchor.getPerformedAt(),
                    null,
                    anchor.getWorkOrderId(),
                    anchor.getPprTaskId(),
                    anchor.getMaintenanceDueEventId(),
                    anchor.getRegulationId(),
                    anchor.getEquipmentMaintenanceRuleId(),
                    anchor.getPlannedDueAt()));
        }
        for (WorkOrder workOrder : safe(workOrders)) {
            if (!isCompleted(workOrder.getStatus())) {
                continue;
            }
            if (anchoredWorkOrders.contains(workOrder.getId())) {
                issues.add(new EquipmentLifecycleDataQuality.QualityIssue(
                        "maintenanceHistory",
                        "DUPLICATE_SOURCE_SUPPRESSED",
                        EquipmentLifecycleDataQuality.IssueSeverity.INFO,
                        "Linked Work Order fallback was suppressed because a completion anchor is canonical",
                        List.of(workOrder.getId())));
                continue;
            }
            if (workOrder.getCompletedAt() == null) {
                issues.add(new EquipmentLifecycleDataQuality.QualityIssue(
                        "maintenanceHistory",
                        "MISSING_ACTUAL_COMPLETION_TIME",
                        EquipmentLifecycleDataQuality.IssueSeverity.WARNING,
                        "Terminal Work Order has no canonical actual completion timestamp",
                        List.of(workOrder.getId())));
            }
            events.add(new EquipmentLifecycleContextV1.MaintenanceEvent(
                    workOrder.getId(),
                    "WORK_ORDER",
                    workOrder.getCompletedAt(),
                    workOrder.getStatus().name(),
                    workOrder.getId(),
                    workOrder.getPprTaskId(),
                    workOrder.getMaintenanceDueEventId(),
                    null,
                    null,
                    null));
        }
        return events.stream()
                .distinct()
                .sorted(MAINTENANCE_ORDER)
                .toList();
    }

    public List<EquipmentLifecycleContextV1.DefectItem> defects(
            List<Defect> defects,
            List<EquipmentLifecycleDataQuality.QualityIssue> issues
    ) {
        List<Defect> source = safe(defects);
        if (!source.isEmpty()) {
            issues.add(new EquipmentLifecycleDataQuality.QualityIssue(
                    "defects",
                    "UNRELIABLE_RECURRENCE",
                    EquipmentLifecycleDataQuality.IssueSeverity.WARNING,
                    "Stored recurrenceCount is not canonical because normalized failure grouping is unavailable",
                    source.stream().map(Defect::getId).filter(java.util.Objects::nonNull).toList()));
        }
        return source.stream()
                .map(defect -> new EquipmentLifecycleContextV1.DefectItem(
                        defect.getId(),
                        defect.getEquipmentNodeId(),
                        defect.getRepairRequestId(),
                        bounded(defect.getCategory()),
                        bounded(defect.getSeverity()),
                        bounded(defect.getFailureReason()),
                        bounded(defect.getRootCause()),
                        name(defect.getStatus()),
                        defect.getDetectedAt(),
                        defect.getResolvedAt(),
                        null,
                        EquipmentLifecycleDataQuality.SourceReliability.UNRELIABLE))
                .sorted(Comparator.comparing(
                                EquipmentLifecycleContextV1.DefectItem::detectedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EquipmentLifecycleContextV1.DefectItem::sourceId))
                .toList();
    }

    public ComponentViews components(List<SparePartInstallation> installations) {
        List<SparePartInstallation> source = safe(installations).stream()
                .sorted(Comparator.comparing(
                                SparePartInstallation::getInstalledAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SparePartInstallation::getId))
                .toList();
        List<EquipmentLifecycleContextV1.InstalledComponentItem> installed = source.stream()
                .filter(item -> item.getStatus() == SparePartInstallationStatus.ACTIVE
                        && item.getRemovedAt() == null)
                .map(this::installedComponent)
                .toList();
        List<EquipmentLifecycleContextV1.ComponentReplacementItem> replacements = source.stream()
                .filter(item -> item.getStatus() == SparePartInstallationStatus.REMOVED
                        || item.getStatus() == SparePartInstallationStatus.REPLACED
                        || item.getRemovedAt() != null)
                .map(this::componentReplacement)
                .toList();
        return new ComponentViews(installed, replacements);
    }

    private EquipmentLifecycleContextV1.InstalledComponentItem installedComponent(
            SparePartInstallation item
    ) {
        return new EquipmentLifecycleContextV1.InstalledComponentItem(
                item.getId(),
                item.getEquipmentNodeId(),
                item.getNormalizedSlotCode(),
                item.getSparePartId(),
                item.getQuantity(),
                name(item.getStatus()),
                item.getInstalledAt(),
                item.getInstallWorkOrderId(),
                item.getSourceMaterialUsageId(),
                item.getAppliedLifeRuleId(),
                item.getAppliedRuleRevision(),
                name(item.getLifecycleEvaluationState()),
                item.getNextCalendarDueAt());
    }

    private EquipmentLifecycleContextV1.ComponentReplacementItem componentReplacement(
            SparePartInstallation item
    ) {
        return new EquipmentLifecycleContextV1.ComponentReplacementItem(
                item.getId(),
                item.getSparePartId(),
                item.getInstalledAt(),
                item.getRemovedAt(),
                item.getInstallWorkOrderId(),
                item.getRemoveWorkOrderId(),
                name(item.getRemovalDisposition()),
                item.getReplacesInstallationId(),
                item.getReplacedByInstallationId(),
                item.getReplacementCorrelationId());
    }

    private boolean isCompleted(WorkOrderStatus status) {
        return status == WorkOrderStatus.COMPLETED || status == WorkOrderStatus.CLOSED;
    }

    private String bounded(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.substring(0, Math.min(256, normalized.length()));
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record ComponentViews(
            List<EquipmentLifecycleContextV1.InstalledComponentItem> installed,
            List<EquipmentLifecycleContextV1.ComponentReplacementItem> replacements
    ) {
        public ComponentViews {
            installed = installed == null ? List.of() : List.copyOf(installed);
            replacements = replacements == null ? List.of() : List.copyOf(replacements);
        }
    }
}

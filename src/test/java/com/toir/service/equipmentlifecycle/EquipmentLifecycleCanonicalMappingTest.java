package com.toir.service.equipmentlifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.toir.dto.equipmentlifecycle.EquipmentLifecycleContextV1;
import com.toir.dto.equipmentlifecycle.EquipmentLifecycleDataQuality;
import com.toir.entity.PprTask;
import com.toir.entity.defects.Defect;
import com.toir.entity.maintenance.MaintenanceCompletionAnchor;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.enums.DefectStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.sparepartlifecycle.SparePartInstallationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentLifecycleCanonicalMappingTest {

    private final EquipmentLifecycleCanonicalMapper mapper =
            new EquipmentLifecycleCanonicalMapper();

    @Test
    void anchorTakesPrecedenceAndSuppressesLinkedWorkOrderFallback() {
        UUID workOrderId = UUID.randomUUID();
        WorkOrder workOrder = workOrder(workOrderId, WorkOrderStatus.CLOSED,
                Instant.parse("2026-07-30T10:00:00Z"));
        MaintenanceCompletionAnchor anchor = new MaintenanceCompletionAnchor();
        anchor.setId(UUID.randomUUID());
        anchor.setWorkOrderId(workOrderId);
        anchor.setPerformedAt(Instant.parse("2026-07-30T09:00:00Z"));
        List<EquipmentLifecycleDataQuality.QualityIssue> issues = new ArrayList<>();

        List<EquipmentLifecycleContextV1.MaintenanceEvent> events =
                mapper.maintenanceEvents(List.of(anchor), List.of(workOrder), issues);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.sourceType()).isEqualTo("MAINTENANCE_COMPLETION_ANCHOR");
            assertThat(event.actualCompletionAt()).isEqualTo(anchor.getPerformedAt());
            assertThat(event.workOrderId()).isEqualTo(workOrderId);
        });
        assertThat(issues).extracting(EquipmentLifecycleDataQuality.QualityIssue::code)
                .contains("DUPLICATE_SOURCE_SUPPRESSED");
    }

    @Test
    void completedAndClosedWorkOrdersAreFallbackButCancelledIsNot() {
        WorkOrder completed = workOrder(UUID.randomUUID(), WorkOrderStatus.COMPLETED,
                Instant.parse("2026-07-29T10:00:00Z"));
        WorkOrder closed = workOrder(UUID.randomUUID(), WorkOrderStatus.CLOSED,
                Instant.parse("2026-07-30T10:00:00Z"));
        WorkOrder cancelled = workOrder(UUID.randomUUID(), WorkOrderStatus.CANCELLED,
                Instant.parse("2026-07-31T10:00:00Z"));

        List<EquipmentLifecycleContextV1.MaintenanceEvent> events =
                mapper.maintenanceEvents(List.of(), List.of(cancelled, closed, completed),
                        new ArrayList<>());

        assertThat(events).extracting(EquipmentLifecycleContextV1.MaintenanceEvent::sourceId)
                .containsExactly(completed.getId(), closed.getId());
    }

    @Test
    void closedWithoutActualCompletionKeepsNullAndAddsWarning() {
        WorkOrder closed = workOrder(UUID.randomUUID(), WorkOrderStatus.CLOSED, null);
        List<EquipmentLifecycleDataQuality.QualityIssue> issues = new ArrayList<>();

        var event = mapper.maintenanceEvents(List.of(), List.of(closed), issues).getFirst();

        assertThat(event.actualCompletionAt()).isNull();
        assertThat(issues).extracting(EquipmentLifecycleDataQuality.QualityIssue::code)
                .contains("MISSING_ACTUAL_COMPLETION_TIME");
    }

    @Test
    void pprLinkIsProvenanceNotASecondCompletion() {
        PprTask pprTask = new PprTask();
        pprTask.setId(UUID.randomUUID());
        WorkOrder workOrder = workOrder(UUID.randomUUID(), WorkOrderStatus.COMPLETED,
                Instant.parse("2026-07-30T10:00:00Z"));
        workOrder.setPprTaskId(pprTask.getId());

        var events = mapper.maintenanceEvents(
                List.of(), List.of(workOrder), new ArrayList<>());

        assertThat(events).singleElement()
                .extracting(EquipmentLifecycleContextV1.MaintenanceEvent::pprTaskId)
                .isEqualTo(pprTask.getId());
    }

    @Test
    void knownAnchorOutsideReturnedSliceStillSuppressesWorkOrderFallback() {
        WorkOrder workOrder = workOrder(
                UUID.randomUUID(),
                WorkOrderStatus.COMPLETED,
                Instant.parse("2026-07-30T10:00:00Z"));

        var events = mapper.maintenanceEvents(
                List.of(),
                List.of(workOrder),
                Set.of(workOrder.getId()),
                new ArrayList<>());

        assertThat(events).isEmpty();
    }

    @Test
    void storedRecurrenceIsNotExposedAsCanonicalCount() {
        Defect defect = new Defect();
        defect.setId(UUID.randomUUID());
        defect.setStatus(DefectStatus.RESOLVED);
        defect.setDetectedAt(Instant.parse("2026-07-30T10:00:00Z"));
        defect.setRecurrenceCount(7);
        List<EquipmentLifecycleDataQuality.QualityIssue> issues = new ArrayList<>();

        var mapped = mapper.defects(List.of(defect), issues).getFirst();

        assertThat(mapped.derivedRepeatedFailureCount()).isNull();
        assertThat(mapped.recurrenceReliability())
                .isEqualTo(EquipmentLifecycleDataQuality.SourceReliability.UNRELIABLE);
        assertThat(issues).extracting(EquipmentLifecycleDataQuality.QualityIssue::code)
                .contains("UNRELIABLE_RECURRENCE");
    }

    @Test
    void canonicalInstallationsProduceCurrentAndReplacementViews() {
        SparePartInstallation active = installation(
                UUID.randomUUID(), SparePartInstallationStatus.ACTIVE, null);
        SparePartInstallation removed = installation(
                UUID.randomUUID(), SparePartInstallationStatus.REPLACED,
                Instant.parse("2026-07-30T10:00:00Z"));

        var components = mapper.components(List.of(removed, active));

        assertThat(components.installed()).extracting(
                EquipmentLifecycleContextV1.InstalledComponentItem::sourceId)
                .containsExactly(active.getId());
        assertThat(components.replacements()).extracting(
                EquipmentLifecycleContextV1.ComponentReplacementItem::sourceId)
                .containsExactly(removed.getId());
    }

    private WorkOrder workOrder(UUID id, WorkOrderStatus status, Instant completedAt) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setStatus(status);
        workOrder.setCompletedAt(completedAt);
        return workOrder;
    }

    private SparePartInstallation installation(
            UUID id,
            SparePartInstallationStatus status,
            Instant removedAt
    ) {
        SparePartInstallation installation = new SparePartInstallation();
        installation.setId(id);
        installation.setSparePartId(UUID.randomUUID());
        installation.setNormalizedSlotCode("BEARING-A");
        installation.setQuantity(BigDecimal.ONE);
        installation.setStatus(status);
        installation.setInstalledAt(Instant.parse("2026-01-01T00:00:00Z"));
        installation.setRemovedAt(removedAt);
        return installation;
    }
}

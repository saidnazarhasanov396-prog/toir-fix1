package com.toir.service.equipmentfleetlifecycle;

import com.toir.dto.equipmentfleetlifecycle.EquipmentFleetLifecycleV1;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.EquipmentRow;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.LatestReadingRow;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.MeterRow;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.RepairMeterSnapshotRow;
import com.toir.repository.equipmentfleetlifecycle.EquipmentFleetLifecycleQueryRepository.RepairRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EquipmentFleetLifecycleBatchAssembler {

    private static final Comparator<String> TEXT_ORDER = Comparator.nullsLast(String::compareTo);
    private static final Comparator<UUID> UUID_ORDER =
            Comparator.nullsLast(Comparator.comparing(UUID::toString));
    private static final Comparator<MeterRow> METER_ORDER = Comparator
            .comparing(MeterRow::meterType, TEXT_ORDER)
            .thenComparing(MeterRow::name, TEXT_ORDER)
            .thenComparing(MeterRow::meterId, UUID_ORDER);
    private static final Comparator<RepairRow> REPAIR_ORDER = Comparator
            .comparing(RepairRow::completedAt, Comparator.nullsLast(Instant::compareTo))
            .thenComparing(RepairRow::workOrderId, UUID_ORDER);

    public List<EquipmentFleetLifecycleV1.Line> assemble(
            List<EquipmentRow> equipment,
            List<MeterRow> meters,
            List<LatestReadingRow> latestReadings,
            List<RepairRow> repairs,
            List<RepairMeterSnapshotRow> snapshots,
            Instant generatedAt) {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(meters, "meters");
        Objects.requireNonNull(latestReadings, "latestReadings");
        Objects.requireNonNull(repairs, "repairs");
        Objects.requireNonNull(snapshots, "snapshots");
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt is required");
        }

        Map<UUID, List<MeterRow>> metersByEquipment = groupMeters(meters);
        Map<EquipmentMeterKey, LatestReadingRow> latestByMeter = indexLatestReadings(latestReadings);
        Map<UUID, List<RepairRow>> repairsByEquipment = groupRepairs(repairs);
        Map<RepairMeterKey, RepairMeterSnapshotRow> snapshotsByRepairMeter = indexSnapshots(snapshots);

        List<EquipmentRow> orderedEquipment = new ArrayList<>(equipment);
        orderedEquipment.sort(Comparator.comparing(EquipmentRow::id, UUID_ORDER));
        List<EquipmentFleetLifecycleV1.Line> lines = new ArrayList<>(orderedEquipment.size());
        for (EquipmentRow equipmentRow : orderedEquipment) {
            lines.add(assembleLine(
                    equipmentRow,
                    metersByEquipment.getOrDefault(equipmentRow.id(), List.of()),
                    latestByMeter,
                    repairsByEquipment.getOrDefault(equipmentRow.id(), List.of()),
                    snapshotsByRepairMeter,
                    generatedAt));
        }
        return List.copyOf(lines);
    }

    private EquipmentFleetLifecycleV1.Line assembleLine(
            EquipmentRow equipment,
            List<MeterRow> meterRows,
            Map<EquipmentMeterKey, LatestReadingRow> latestByMeter,
            List<RepairRow> repairRows,
            Map<RepairMeterKey, RepairMeterSnapshotRow> snapshotsByRepairMeter,
            Instant generatedAt) {
        LinkedHashMap<IssueKey, EquipmentFleetLifecycleV1.QualityIssue> issues = new LinkedHashMap<>();

        List<MeterRow> orderedMeterRows = new ArrayList<>(meterRows);
        orderedMeterRows.sort(METER_ORDER);
        List<EquipmentFleetLifecycleV1.Meter> assembledMeters = new ArrayList<>(orderedMeterRows.size());
        if (orderedMeterRows.isEmpty()) {
            addIssue(issues, EquipmentFleetLifecycleV1.QualityIssueCode.NO_METERS,
                    "Equipment has no meters", List.of(equipment.id()));
        }
        for (MeterRow meter : orderedMeterRows) {
            LatestReadingRow latest = latestByMeter.get(new EquipmentMeterKey(equipment.id(), meter.meterId()));
            if (latest == null) {
                addIssue(issues, EquipmentFleetLifecycleV1.QualityIssueCode.METER_WITHOUT_READING,
                        "Meter has no latest reading", List.of(meter.meterId()));
            } else if (!Objects.equals(meter.cachedCurrentValue(), latest.value())
                    || !Objects.equals(meter.cachedLastReadAt(), latest.readAt())) {
                addIssue(issues, EquipmentFleetLifecycleV1.QualityIssueCode.METER_CACHE_MISMATCH,
                        "Cached meter value or time differs from latest reading", List.of(meter.meterId()));
            }
            assembledMeters.add(mapMeter(meter, latest));
        }

        List<RepairRow> orderedRepairRows = new ArrayList<>(repairRows);
        orderedRepairRows.sort(REPAIR_ORDER);
        List<EquipmentFleetLifecycleV1.Repair> assembledRepairs = new ArrayList<>(orderedRepairRows.size());
        if (orderedRepairRows.isEmpty()) {
            addIssue(issues, EquipmentFleetLifecycleV1.QualityIssueCode.NO_COMPLETED_REPAIRS,
                    "Equipment has no completed repairs", List.of(equipment.id()));
        }
        for (RepairRow repair : orderedRepairRows) {
            assembledRepairs.add(mapRepair(
                    equipment.id(), repair, orderedMeterRows, snapshotsByRepairMeter, issues));
        }

        List<EquipmentFleetLifecycleV1.QualityIssue> issueList = List.copyOf(issues.values());
        EquipmentFleetLifecycleV1.Repair lastRepair = assembledRepairs.isEmpty()
                ? null
                : assembledRepairs.get(assembledRepairs.size() - 1);
        return new EquipmentFleetLifecycleV1.Line(
                EquipmentFleetLifecycleV1.SCHEMA_VERSION,
                generatedAt,
                EquipmentFleetLifecycleV1.CONSISTENCY,
                mapEquipment(equipment),
                assembledMeters,
                lastRepair,
                assembledRepairs,
                new EquipmentFleetLifecycleV1.DataQuality(issueList.isEmpty(), issueList));
    }

    private EquipmentFleetLifecycleV1.EquipmentCore mapEquipment(EquipmentRow row) {
        return new EquipmentFleetLifecycleV1.EquipmentCore(
                row.id(),
                row.code(),
                row.name(),
                row.inventoryNumber(),
                row.technicalNumber(),
                row.serialNumber(),
                row.model(),
                row.equipmentTypeId(),
                row.departmentId(),
                row.responsibleDepartmentId(),
                row.locationId(),
                row.criticalityClassId(),
                row.status(),
                row.category(),
                row.manufacturer(),
                row.commissionedAt(),
                row.arrivalDate(),
                row.operationStartDate(),
                row.expectedLifetimeMonths(),
                row.expectedLifetimeYears(),
                row.expectedLifetimeHours(),
                row.lifetimeCounterType(),
                row.lifetimeMeterId(),
                row.lifetimeLimitValue());
    }

    private EquipmentFleetLifecycleV1.Meter mapMeter(MeterRow meter, LatestReadingRow latest) {
        return new EquipmentFleetLifecycleV1.Meter(
                meter.meterId(),
                meter.name(),
                meter.meterType(),
                meter.unit(),
                meter.active(),
                meter.primary(),
                meter.rolloverValue(),
                meter.cachedCurrentValue(),
                meter.cachedLastReadAt(),
                latest == null ? null : mapReading(latest));
    }

    private EquipmentFleetLifecycleV1.Reading mapReading(LatestReadingRow row) {
        return new EquipmentFleetLifecycleV1.Reading(
                row.readingId(),
                row.value(),
                row.delta(),
                row.readAt(),
                row.source(),
                row.readingContext(),
                row.repairRequestId(),
                row.workOrderId(),
                row.defectId());
    }

    private EquipmentFleetLifecycleV1.Repair mapRepair(
            UUID equipmentId,
            RepairRow repair,
            List<MeterRow> orderedMeters,
            Map<RepairMeterKey, RepairMeterSnapshotRow> snapshotsByRepairMeter,
            LinkedHashMap<IssueKey, EquipmentFleetLifecycleV1.QualityIssue> issues) {
        if (repair.defectId() != null && !repair.defectLinkValid()) {
            addIssue(issues, EquipmentFleetLifecycleV1.QualityIssueCode.BROKEN_DEFECT_LINK,
                    "Repair has an invalid or cross-equipment Defect link",
                    List.of(repair.workOrderId(), repair.defectId()));
        }
        if (repair.repairRequestId() != null && !repair.repairRequestLinkValid()) {
            addIssue(issues, EquipmentFleetLifecycleV1.QualityIssueCode.BROKEN_REPAIR_REQUEST_LINK,
                    "Repair has an invalid or cross-equipment Repair Request link",
                    List.of(repair.workOrderId(), repair.repairRequestId()));
        }

        EquipmentFleetLifecycleV1.RepairReason reason = mapReason(repair);
        if (reason == null) {
            addIssue(issues, EquipmentFleetLifecycleV1.QualityIssueCode.REPAIR_WITHOUT_REASON,
                    "Repair has no valid linked failure reason", List.of(repair.workOrderId()));
        }

        List<EquipmentFleetLifecycleV1.RepairMeterSnapshot> assembledSnapshots =
                new ArrayList<>(orderedMeters.size());
        for (MeterRow meter : orderedMeters) {
            RepairMeterSnapshotRow snapshot = snapshotsByRepairMeter.get(
                    new RepairMeterKey(equipmentId, repair.workOrderId(), meter.meterId()));
            boolean available = snapshot != null && snapshot.readingId() != null;
            if (!available) {
                addIssue(issues, EquipmentFleetLifecycleV1.QualityIssueCode.REPAIR_METER_READING_MISSING,
                        "Repair has no meter reading at completion",
                        List.of(repair.workOrderId(), meter.meterId()));
            }
            assembledSnapshots.add(new EquipmentFleetLifecycleV1.RepairMeterSnapshot(
                    meter.meterId(),
                    meter.meterType(),
                    meter.unit(),
                    available ? "AVAILABLE" : "MISSING",
                    available ? snapshot.readingId() : null,
                    available ? snapshot.value() : null,
                    available ? snapshot.readAt() : null));
        }

        return new EquipmentFleetLifecycleV1.Repair(
                repair.workOrderId(),
                repair.workOrderNumber(),
                repair.title(),
                repair.repairType(),
                repair.workType(),
                repair.status(),
                repair.priority(),
                repair.equipmentNodeId(),
                repair.defectId(),
                repair.repairRequestId(),
                repair.pprTaskId(),
                repair.maintenanceDueEventId(),
                repair.startPlannedAt(),
                repair.endPlannedAt(),
                repair.startedAt(),
                repair.completedAt(),
                repair.durationMinutes(),
                repair.summary(),
                repair.result(),
                repair.closureNotes(),
                reason,
                assembledSnapshots);
    }

    private EquipmentFleetLifecycleV1.RepairReason mapReason(RepairRow repair) {
        if (repair.defectId() != null && repair.defectLinkValid()) {
            return new EquipmentFleetLifecycleV1.RepairReason(
                    "DEFECT",
                    bounded(repair.defectDescription()),
                    bounded(repair.failureReason()),
                    bounded(repair.rootCause()));
        }
        if (repair.repairRequestId() != null && repair.repairRequestLinkValid()) {
            return new EquipmentFleetLifecycleV1.RepairReason(
                    "REPAIR_REQUEST", bounded(repair.repairRequestDescription()), null, null);
        }
        return null;
    }

    private String bounded(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder normalized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            normalized.append(Character.isISOControl(character) && Character.isWhitespace(character)
                    ? ' '
                    : character);
        }
        String trimmed = normalized.toString().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.substring(0, Math.min(512, trimmed.length()));
    }

    private Map<UUID, List<MeterRow>> groupMeters(List<MeterRow> meters) {
        Map<UUID, List<MeterRow>> grouped = new HashMap<>();
        for (MeterRow meter : meters) {
            grouped.computeIfAbsent(meter.equipmentId(), ignored -> new ArrayList<>()).add(meter);
        }
        return grouped;
    }

    private Map<EquipmentMeterKey, LatestReadingRow> indexLatestReadings(
            List<LatestReadingRow> latestReadings) {
        Map<EquipmentMeterKey, LatestReadingRow> indexed = new HashMap<>();
        for (LatestReadingRow reading : latestReadings) {
            indexed.put(new EquipmentMeterKey(reading.equipmentId(), reading.meterId()), reading);
        }
        return indexed;
    }

    private Map<UUID, List<RepairRow>> groupRepairs(List<RepairRow> repairs) {
        Map<UUID, List<RepairRow>> grouped = new HashMap<>();
        for (RepairRow repair : repairs) {
            grouped.computeIfAbsent(repair.equipmentId(), ignored -> new ArrayList<>()).add(repair);
        }
        return grouped;
    }

    private Map<RepairMeterKey, RepairMeterSnapshotRow> indexSnapshots(
            List<RepairMeterSnapshotRow> snapshots) {
        Map<RepairMeterKey, RepairMeterSnapshotRow> indexed = new HashMap<>();
        for (RepairMeterSnapshotRow snapshot : snapshots) {
            indexed.put(new RepairMeterKey(
                    snapshot.equipmentId(), snapshot.workOrderId(), snapshot.meterId()), snapshot);
        }
        return indexed;
    }

    private void addIssue(
            LinkedHashMap<IssueKey, EquipmentFleetLifecycleV1.QualityIssue> issues,
            EquipmentFleetLifecycleV1.QualityIssueCode code,
            String message,
            List<UUID> relatedIds) {
        List<UUID> stableRelatedIds = List.copyOf(relatedIds);
        IssueKey key = new IssueKey(code, stableRelatedIds);
        issues.putIfAbsent(
                key, new EquipmentFleetLifecycleV1.QualityIssue(code, message, stableRelatedIds));
    }

    private record EquipmentMeterKey(UUID equipmentId, UUID meterId) {
    }

    private record RepairMeterKey(UUID equipmentId, UUID workOrderId, UUID meterId) {
    }

    private record IssueKey(
            EquipmentFleetLifecycleV1.QualityIssueCode code, List<UUID> relatedIds) {
    }
}

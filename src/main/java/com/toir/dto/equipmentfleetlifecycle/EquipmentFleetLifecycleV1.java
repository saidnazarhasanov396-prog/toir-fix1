package com.toir.dto.equipmentfleetlifecycle;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Immutable JSONL record contract for the fleet lifecycle v1 endpoint. */
public final class EquipmentFleetLifecycleV1 {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String CONSISTENCY = "FIXED_AS_OF_READ_COMMITTED_BATCHES";

    private EquipmentFleetLifecycleV1() {
    }

    public record Line(
            String schemaVersion,
            Instant generatedAt,
            String consistency,
            EquipmentCore equipment,
            List<Meter> meters,
            Repair lastRepair,
            List<Repair> repairs,
            DataQuality dataQuality
    ) {
        public Line {
            if (generatedAt == null) {
                throw new IllegalArgumentException("generatedAt is required");
            }
            if (!SCHEMA_VERSION.equals(schemaVersion)) {
                throw new IllegalArgumentException("schemaVersion must be " + SCHEMA_VERSION);
            }
            if (!CONSISTENCY.equals(consistency)) {
                throw new IllegalArgumentException("consistency must be " + CONSISTENCY);
            }
            meters = meters == null ? List.of() : List.copyOf(meters);
            repairs = repairs == null ? List.of() : List.copyOf(repairs);
            dataQuality = dataQuality == null ? new DataQuality(true, List.of()) : dataQuality;
        }
    }

    public record EquipmentCore(
            UUID id,
            String code,
            String name,
            String inventoryNumber,
            String technicalNumber,
            String serialNumber,
            String model,
            UUID equipmentTypeId,
            UUID departmentId,
            UUID responsibleDepartmentId,
            UUID locationId,
            UUID criticalityClassId,
            String status,
            String category,
            String manufacturer,
            LocalDate commissionedAt,
            LocalDate arrivalDate,
            LocalDate operationStartDate,
            Integer expectedLifetimeMonths,
            Integer expectedLifetimeYears,
            Long expectedLifetimeHours,
            String lifetimeCounterType,
            UUID lifetimeMeterId,
            Double lifetimeLimitValue
    ) {}

    public record Meter(
            UUID meterId,
            String name,
            String meterType,
            String unit,
            boolean active,
            boolean primary,
            Double rolloverValue,
            Double cachedCurrentValue,
            Instant cachedLastReadAt,
            Reading latestReading
    ) {}

    public record Reading(
            UUID readingId,
            Double value,
            Double delta,
            Instant readAt,
            String source,
            String readingContext,
            UUID repairRequestId,
            UUID workOrderId,
            UUID defectId
    ) {}

    public record Repair(
            UUID workOrderId,
            String workOrderNumber,
            String title,
            String repairType,
            String workType,
            String status,
            String priority,
            UUID equipmentNodeId,
            UUID defectId,
            UUID repairRequestId,
            UUID pprTaskId,
            UUID maintenanceDueEventId,
            Instant startPlannedAt,
            Instant endPlannedAt,
            Instant startedAt,
            Instant completedAt,
            Long durationMinutes,
            String summary,
            String result,
            String closureNotes,
            RepairReason reason,
            List<RepairMeterSnapshot> meterReadingsAtRepair
    ) {
        public Repair {
            meterReadingsAtRepair = meterReadingsAtRepair == null ? List.of() : List.copyOf(meterReadingsAtRepair);
        }
    }

    public record RepairReason(String source, String description, String failureReason, String rootCause) {}

    public record RepairMeterSnapshot(
            UUID meterId,
            String meterType,
            String unit,
            String availability,
            UUID readingId,
            Double value,
            Instant readAt
    ) {}

    public record DataQuality(boolean complete, List<QualityIssue> issues) {
        public DataQuality {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public record QualityIssue(QualityIssueCode code, String message, List<UUID> relatedIds) {
        public QualityIssue {
            relatedIds = relatedIds == null ? List.of() : List.copyOf(relatedIds);
        }
    }

    public enum QualityIssueCode {
        NO_METERS,
        METER_WITHOUT_READING,
        NO_COMPLETED_REPAIRS,
        REPAIR_WITHOUT_REASON,
        REPAIR_METER_READING_MISSING,
        BROKEN_DEFECT_LINK,
        BROKEN_REPAIR_REQUEST_LINK,
        METER_CACHE_MISMATCH
    }
}

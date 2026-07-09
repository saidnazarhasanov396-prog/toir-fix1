package com.toir.service;

import com.toir.entity.DowntimeEvent;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DowntimeType;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkOrderType;
import com.toir.enums.WorkType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class ReliabilityDowntimeCalculator {

    private ReliabilityDowntimeCalculator() {
    }

    static EquipmentReliability calculate(Equipment equipment,
                                          List<DowntimeEvent> downtimes,
                                          List<WorkOrder> workOrders,
                                          List<RepairRequest> repairRequests,
                                          Instant now) {
        Instant periodStart = observationStart(equipment, now);
        List<DowntimeSlice> failureSlices = failureDowntimes(downtimes, workOrders, repairRequests, periodStart, now);
        long totalDowntimeMinutes = mergedDowntimeMinutes(failureSlices);
        long completedEvents = failureSlices.stream()
                .filter(DowntimeSlice::completed)
                .count();
        long completedRepairMinutes = failureSlices.stream()
                .filter(DowntimeSlice::completed)
                .mapToLong(DowntimeSlice::durationMinutes)
                .sum();

        Double mttrHours = completedEvents > 0
                ? (completedRepairMinutes / 60.0) / completedEvents
                : null;
        double observedHours = Math.max(Duration.between(periodStart, now).toMinutes() / 60.0, 0.0);
        double operatingHours = Math.max(observedHours - totalDowntimeMinutes / 60.0, 0.0);
        Double mtbfHours = failureSlices.isEmpty() ? null : operatingHours / failureSlices.size();
        double availabilityPct = observedHours > 0 ? operatingHours / observedHours * 100.0 : 100.0;

        return new EquipmentReliability(
                failureSlices.size(),
                totalDowntimeMinutes,
                observedHours,
                operatingHours,
                mtbfHours,
                mttrHours,
                availabilityPct,
                failureSlices
        );
    }

    static Instant observationStart(Equipment equipment, Instant now) {
        LocalDate serviceStart = equipment.getOperationStartDate() != null
                ? equipment.getOperationStartDate()
                : equipment.getCommissionedAt();
        Instant start = serviceStart != null
                ? serviceStart.atStartOfDay(ZoneOffset.UTC).toInstant()
                : equipment.getCreatedAt();
        if (start == null || start.isAfter(now)) {
            return now;
        }
        return start;
    }

    static List<DowntimeSlice> failureDowntimes(List<DowntimeEvent> downtimes,
                                                List<WorkOrder> workOrders,
                                                List<RepairRequest> repairRequests,
                                                Instant periodStart,
                                                Instant periodEnd) {
        List<DowntimeSlice> result = new java.util.ArrayList<>();
        Set<UUID> representedWorkOrderIds = new HashSet<>();
        Map<UUID, WorkOrder> workOrdersById = workOrders.stream()
                .filter(workOrder -> workOrder.getId() != null)
                .collect(Collectors.toMap(WorkOrder::getId, workOrder -> workOrder, (first, second) -> first));

        downtimes.stream()
                .filter(ReliabilityDowntimeCalculator::isFailureDowntime)
                .forEach(event -> {
                    DowntimeSlice slice = sliceToPeriod(
                            event.getEquipmentId(),
                            event.getDepartmentId(),
                            event.getType() != null ? event.getType().name() : "UNKNOWN",
                            DowntimeSourceType.DOWNTIME_EVENT,
                            event.getId(),
                            event,
                            periodStart,
                            periodEnd);
                    if (slice.durationMinutes() > 0) {
                        result.add(slice);
                        if (event.getWorkOrderId() != null) {
                            representedWorkOrderIds.add(event.getWorkOrderId());
                        }
                    }
                });

        Set<UUID> representedRepairRequestIds = new HashSet<>();
        representedWorkOrderIds.stream()
                .map(workOrdersById::get)
                .filter(java.util.Objects::nonNull)
                .map(WorkOrder::getRepairRequestId)
                .filter(java.util.Objects::nonNull)
                .forEach(representedRepairRequestIds::add);

        workOrders.stream()
                .filter(ReliabilityDowntimeCalculator::isRepairWorkOrder)
                .filter(workOrder -> workOrder.getId() == null || !representedWorkOrderIds.contains(workOrder.getId()))
                .forEach(workOrder -> {
                    DowntimeSlice slice = sliceToPeriod(
                            workOrder.getEquipmentId(),
                            workOrder.getDepartmentId(),
                            workOrderCauseKey(workOrder),
                            DowntimeSourceType.WORK_ORDER,
                            workOrder.getId(),
                            workOrder.getStartedAt(),
                            workOrder.getCompletedAt(),
                            workOrder.getStatus() == WorkOrderStatus.COMPLETED
                                    || workOrder.getStatus() == WorkOrderStatus.CLOSED,
                            periodStart,
                            periodEnd);
                    if (slice.durationMinutes() > 0) {
                        result.add(slice);
                        if (workOrder.getRepairRequestId() != null) {
                            representedRepairRequestIds.add(workOrder.getRepairRequestId());
                        }
                    }
                });

        repairRequests.stream()
                .filter(ReliabilityDowntimeCalculator::isReliabilityRepairRequest)
                .filter(request -> request.getId() == null || !representedRepairRequestIds.contains(request.getId()))
                .map(request -> sliceToPeriod(
                        request.getEquipmentId(),
                        request.getDepartmentId(),
                        request.getPriority() != null ? request.getPriority().name() : "REPAIR_REQUEST",
                        DowntimeSourceType.REPAIR_REQUEST,
                        request.getId(),
                        request.getDetectedAt(),
                        request.getActualCompletionAt(),
                        request.getStatus() == RequestStatus.COMPLETED || request.getStatus() == RequestStatus.CLOSED,
                        periodStart,
                        periodEnd))
                .filter(slice -> slice.durationMinutes() > 0)
                .forEach(result::add);

        return result;
    }

    static long mergedDowntimeMinutes(List<DowntimeSlice> slices) {
        List<DowntimeSlice> sorted = slices.stream()
                .sorted(Comparator.comparing(DowntimeSlice::start))
                .toList();
        if (sorted.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        Instant currentStart = sorted.getFirst().start();
        Instant currentEnd = sorted.getFirst().end();
        for (int i = 1; i < sorted.size(); i++) {
            DowntimeSlice next = sorted.get(i);
            if (!next.start().isAfter(currentEnd)) {
                if (next.end().isAfter(currentEnd)) {
                    currentEnd = next.end();
                }
            } else {
                total += Duration.between(currentStart, currentEnd).toMinutes();
                currentStart = next.start();
                currentEnd = next.end();
            }
        }
        return total + Duration.between(currentStart, currentEnd).toMinutes();
    }

    private static boolean isFailureDowntime(DowntimeEvent event) {
        return event.getType() == DowntimeType.UNPLANNED || event.getType() == DowntimeType.EMERGENCY;
    }

    private static boolean isRepairWorkOrder(WorkOrder workOrder) {
        return workOrder.getWorkType() == WorkType.REPAIR
                && workOrder.getStartedAt() != null
                && workOrder.getStatus() != WorkOrderStatus.CANCELLED;
    }

    private static boolean isReliabilityRepairRequest(RepairRequest request) {
        return request.getDetectedAt() != null
                && request.getStatus() != RequestStatus.DRAFT
                && request.getStatus() != RequestStatus.REJECTED
                && request.getStatus() != RequestStatus.CANCELLED;
    }

    private static String workOrderCauseKey(WorkOrder workOrder) {
        WorkOrderType type = workOrder.getType();
        if (type != null) {
            return type.name();
        }
        return workOrder.getWorkType() != null ? workOrder.getWorkType().name() : "WORK_ORDER";
    }

    private static DowntimeSlice sliceToPeriod(UUID equipmentId,
                                               UUID departmentId,
                                               String causeKey,
                                               DowntimeSourceType sourceType,
                                               UUID sourceId,
                                               DowntimeEvent event,
                                               Instant periodStart,
                                               Instant periodEnd) {
        if (event.getStartAt() == null) {
            return DowntimeSlice.empty(equipmentId, departmentId, causeKey);
        }

        Instant eventEnd;
        boolean completed;
        if (event.getEndAt() != null) {
            eventEnd = event.getEndAt();
            completed = true;
        } else if (event.getDurationMinutes() != null) {
            eventEnd = event.getStartAt().plus(Duration.ofMinutes(Math.max(event.getDurationMinutes(), 0)));
            completed = true;
        } else {
            eventEnd = periodEnd;
            completed = false;
        }

        return sliceToPeriod(equipmentId, departmentId, causeKey, sourceType, sourceId, event.getStartAt(), eventEnd, completed, periodStart, periodEnd);
    }

    private static DowntimeSlice sliceToPeriod(UUID equipmentId,
                                               UUID departmentId,
                                               String causeKey,
                                               DowntimeSourceType sourceType,
                                               UUID sourceId,
                                               Instant start,
                                               Instant end,
                                               boolean completed,
                                               Instant periodStart,
                                               Instant periodEnd) {
        if (start == null) {
            return DowntimeSlice.empty(equipmentId, departmentId, causeKey);
        }
        Instant effectiveEnd = end != null ? end : periodEnd;
        Instant overlapStart = start.isAfter(periodStart) ? start : periodStart;
        Instant overlapEnd = effectiveEnd.isBefore(periodEnd) ? effectiveEnd : periodEnd;
        if (!overlapEnd.isAfter(overlapStart)) {
            return DowntimeSlice.empty(equipmentId, departmentId, causeKey);
        }
        return new DowntimeSlice(
                equipmentId,
                departmentId,
                causeKey == null || causeKey.isBlank() ? "UNKNOWN" : causeKey,
                sourceType == null ? DowntimeSourceType.UNKNOWN : sourceType,
                sourceId,
                overlapStart,
                overlapEnd,
                Duration.between(overlapStart, overlapEnd).toMinutes(),
                completed
        );
    }

    record EquipmentReliability(int failureEvents,
                                long totalDowntimeMinutes,
                                double observedHours,
                                double operatingHours,
                                Double mtbfHours,
                                Double mttrHours,
                                double availabilityPct,
                                List<DowntimeSlice> failureSlices) {
        EquipmentReliability {
            failureSlices = List.copyOf(failureSlices);
        }
    }

    record DowntimeSlice(UUID equipmentId,
                         UUID departmentId,
                         String causeKey,
                         DowntimeSourceType sourceType,
                         UUID sourceId,
                         Instant start,
                         Instant end,
                         long durationMinutes,
                         boolean completed) {
        private static DowntimeSlice empty(UUID equipmentId, UUID departmentId, String causeKey) {
            Instant epoch = Instant.EPOCH;
            return new DowntimeSlice(equipmentId, departmentId, causeKey, DowntimeSourceType.UNKNOWN, null, epoch, epoch, 0, false);
        }
    }

    enum DowntimeSourceType {
        DOWNTIME_EVENT,
        WORK_ORDER,
        REPAIR_REQUEST,
        UNKNOWN
    }
}

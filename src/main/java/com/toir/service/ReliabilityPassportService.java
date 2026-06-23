package com.toir.service;

import com.toir.controller.ReliabilityPassportController.ReliabilityPassport;
import com.toir.controller.ReliabilityPassportController.ReliabilityPassportStats;
import com.toir.controller.ReliabilityPassportController.TopCause;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.entity.repair.RepairRequest;
import com.toir.enums.DefectStatus;
import com.toir.enums.DowntimeType;
import com.toir.enums.RequestStatus;
import com.toir.enums.WorkOrderStatus;
import com.toir.enums.WorkType;
import com.toir.exception.RestException;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
import com.toir.repository.repair.RepairRequestRepository;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReliabilityPassportService {

    private static final Duration ANALYSIS_PERIOD = Duration.ofDays(365);
    private static final EnumSet<DefectStatus> OPEN_DEFECT_STATUSES =
            EnumSet.of(DefectStatus.OPEN, DefectStatus.IN_ANALYSIS, DefectStatus.IN_PROGRESS);

    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final DowntimeEventRepository downtimeRepository;
    private final WorkOrderRepository workOrderRepository;
    private final RepairRequestRepository repairRequestRepository;

    @Transactional(readOnly = true)
    public Page<ReliabilityPassport> list(UUID equipmentId, String search, String availability, int page, int size) {
        AvailabilityBand availabilityFilter = parseAvailabilityFilter(availability);
        String searchPattern = search == null || search.isBlank()
                ? null
                : "%" + search.toLowerCase() + "%";

        if (availabilityFilter != null) {
            List<Equipment> equipmentList = equipmentRepository.searchAllForPassport(equipmentId, searchPattern);
            if (equipmentList.isEmpty()) {
                return PaginationUtils.page(List.of(), page, size);
            }

            List<ReliabilityPassport> filteredPassports = buildPassports(equipmentList).stream()
                    .filter(passport -> bandOf(passport.availabilityPct()) == availabilityFilter)
                    .toList();
            return PaginationUtils.page(filteredPassports, page, size);
        }

        Page<Equipment> equipmentPage = equipmentRepository.searchForPassport(
                equipmentId,
                searchPattern,
                PaginationUtils.pageRequest(page, size)
        );

        List<UUID> ids = equipmentPage.getContent().stream()
                .map(Equipment::getId)
                .toList();

        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), equipmentPage.getPageable(), equipmentPage.getTotalElements());
        }

        List<ReliabilityPassport> passports = buildPassports(equipmentPage.getContent());

        return new PageImpl<>(passports, equipmentPage.getPageable(), equipmentPage.getTotalElements());
    }

    private List<ReliabilityPassport> buildPassports(List<Equipment> equipmentList) {
        List<UUID> ids = equipmentList.stream()
                .map(Equipment::getId)
                .toList();

        Map<UUID, List<Defect>> defectsByEquipment = defectRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(ids)
                .stream()
                .collect(Collectors.groupingBy(Defect::getEquipmentId));

        Map<UUID, List<DowntimeEvent>> downtimesByEquipment = downtimeRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(ids)
                .stream()
                .collect(Collectors.groupingBy(DowntimeEvent::getEquipmentId));
        Map<UUID, List<WorkOrder>> workOrdersByEquipment = workOrderRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(ids)
                .stream()
                .collect(Collectors.groupingBy(WorkOrder::getEquipmentId));
        Map<UUID, List<RepairRequest>> repairRequestsByEquipment = repairRequestRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(ids)
                .stream()
                .collect(Collectors.groupingBy(RepairRequest::getEquipmentId));

        Instant now = Instant.now();
        return equipmentList.stream()
                .map(eq -> buildPassport(
                        eq,
                        defectsByEquipment.getOrDefault(eq.getId(), List.of()),
                        downtimesByEquipment.getOrDefault(eq.getId(), List.of()),
                        workOrdersByEquipment.getOrDefault(eq.getId(), List.of()),
                        repairRequestsByEquipment.getOrDefault(eq.getId(), List.of()),
                        now))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReliabilityPassportStats stats(UUID equipmentId, String search, String availability) {
        AvailabilityBand availabilityFilter = parseAvailabilityFilter(availability);
        String searchPattern = search == null || search.isBlank()
                ? null
                : "%" + search.toLowerCase() + "%";

        List<Equipment> equipmentList = equipmentRepository.searchAllForPassport(equipmentId, searchPattern);
        if (equipmentList.isEmpty()) {
            return new ReliabilityPassportStats(0, 0, 0, 0);
        }

        List<UUID> ids = equipmentList.stream()
                .map(Equipment::getId)
                .toList();

        Map<UUID, List<DowntimeEvent>> downtimesByEquipment = downtimeRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(ids)
                .stream()
                .collect(Collectors.groupingBy(DowntimeEvent::getEquipmentId));
        Map<UUID, List<WorkOrder>> workOrdersByEquipment = workOrderRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(ids)
                .stream()
                .collect(Collectors.groupingBy(WorkOrder::getEquipmentId));
        Map<UUID, List<RepairRequest>> repairRequestsByEquipment = repairRequestRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(ids)
                .stream()
                .collect(Collectors.groupingBy(RepairRequest::getEquipmentId));

        Instant now = Instant.now();
        int total = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        for (Equipment equipment : equipmentList) {
            double availabilityPct = availabilityPct(
                    equipment,
                    downtimesByEquipment.getOrDefault(equipment.getId(), List.of()),
                    workOrdersByEquipment.getOrDefault(equipment.getId(), List.of()),
                    repairRequestsByEquipment.getOrDefault(equipment.getId(), List.of()),
                    now
            );
            AvailabilityBand band = bandOf(availabilityPct);
            if (availabilityFilter != null && availabilityFilter != band) {
                continue;
            }
            total++;
            switch (band) {
                case HIGH -> high++;
                case MEDIUM -> medium++;
                case LOW -> low++;
            }
        }
        return new ReliabilityPassportStats(total, high, medium, low);
    }

    @Transactional(readOnly = true)
    public ReliabilityPassport passport(UUID equipmentId) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));

        List<Defect> defects = defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId);
        List<DowntimeEvent> downtimes = downtimeRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId);
        List<WorkOrder> workOrders = workOrderRepository.search(null, null, equipmentId);
        List<RepairRequest> repairRequests = repairRequestRepository.search(null, null, equipmentId);

        return buildPassport(equipment, defects, downtimes, workOrders, repairRequests, Instant.now());
    }

    private ReliabilityPassport buildPassport(Equipment equipment,
                                              List<Defect> defects,
                                              List<DowntimeEvent> downtimes,
                                              List<WorkOrder> workOrders,
                                              List<RepairRequest> repairRequests,
                                              Instant now) {
        List<Defect> activeDefects = defects.stream()
                .filter(d -> d.getStatus() != DefectStatus.CANCELLED)
                .toList();
        int openDefects = (int) activeDefects.stream()
                .filter(d -> OPEN_DEFECT_STATUSES.contains(d.getStatus()))
                .count();

        Instant periodStart = analysisPeriodStart(equipment, now);
        List<DowntimeSlice> failureDowntimes =
                reliabilityDowntimes(downtimes, workOrders, repairRequests, periodStart, now);
        long totalDowntimeMinutes = mergedDowntimeMinutes(failureDowntimes);
        long mttrDenominator = failureDowntimes.stream().filter(DowntimeSlice::completed).count();
        long mttrSumMinutes = failureDowntimes.stream()
                .filter(DowntimeSlice::completed)
                .mapToLong(DowntimeSlice::durationMinutes)
                .sum();

        Double mttrHours = mttrDenominator > 0 ? (mttrSumMinutes / 60.0) / mttrDenominator : null;
        double observedHours = Duration.between(periodStart, now).toMinutes() / 60.0;
        double uptimeHours = Math.max(observedHours - totalDowntimeMinutes / 60.0, 0.0);
        Double mtbfHours = failureDowntimes.isEmpty()
                ? null
                : uptimeHours / failureDowntimes.size();

        double availabilityPct = availabilityPct(observedHours, totalDowntimeMinutes);

        Map<String, Integer> causes = new HashMap<>();
        for (Defect d : activeDefects) {
            String key = d.getRootCause() != null && !d.getRootCause().isBlank()
                    ? d.getRootCause()
                    : (d.getFailureReason() != null && !d.getFailureReason().isBlank()
                    ? d.getFailureReason()
                    : "UNKNOWN");
            causes.merge(key, 1, Integer::sum);
        }
        List<TopCause> topCauses = causes.entrySet().stream()
                .map(e -> new TopCause(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(TopCause::count).reversed())
                .limit(10)
                .toList();

        return new ReliabilityPassport(
                equipment.getId(),
                equipment.getCode(),
                equipment.getName(),
                activeDefects.size(),
                openDefects,
                failureDowntimes.size(),
                totalDowntimeMinutes,
                mtbfHours,
                mttrHours,
                availabilityPct,
                topCauses,
                now
        );
    }

    private double availabilityPct(Equipment equipment,
                                   List<DowntimeEvent> downtimes,
                                   List<WorkOrder> workOrders,
                                   List<RepairRequest> repairRequests,
                                   Instant now) {
        Instant periodStart = analysisPeriodStart(equipment, now);
        double observedHours = Duration.between(periodStart, now).toMinutes() / 60.0;
        long downtimeMinutes = mergedDowntimeMinutes(
                reliabilityDowntimes(downtimes, workOrders, repairRequests, periodStart, now));
        return availabilityPct(observedHours, downtimeMinutes);
    }

    private double availabilityPct(double observedHours, long downtimeMinutes) {
        if (observedHours <= 0) {
            return 100.0;
        }
        double uptimeHours = Math.max(observedHours - downtimeMinutes / 60.0, 0.0);
        return uptimeHours / observedHours * 100.0;
    }

    private Instant analysisPeriodStart(Equipment equipment, Instant now) {
        Instant horizon = now.minus(ANALYSIS_PERIOD);
        LocalDate serviceStart = equipment.getOperationStartDate() != null
                ? equipment.getOperationStartDate()
                : equipment.getCommissionedAt();
        if (serviceStart == null) {
            return horizon;
        }
        Instant serviceStartInstant = serviceStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        if (serviceStartInstant.isAfter(now)) {
            return now;
        }
        return serviceStartInstant.isAfter(horizon) ? serviceStartInstant : horizon;
    }

    private AvailabilityBand bandOf(double availabilityPct) {
        if (availabilityPct >= 95.0) return AvailabilityBand.HIGH;
        if (availabilityPct >= 80.0) return AvailabilityBand.MEDIUM;
        return AvailabilityBand.LOW;
    }

    private AvailabilityBand parseAvailabilityFilter(String availability) {
        if (availability == null || availability.isBlank()) {
            return null;
        }
        return switch (availability.toLowerCase()) {
            case "high" -> AvailabilityBand.HIGH;
            case "medium" -> AvailabilityBand.MEDIUM;
            case "low" -> AvailabilityBand.LOW;
            default -> throw RestException.badRequest(
                    "Unknown availability filter: " + availability + ". Allowed values: high, medium, low");
        };
    }

    private enum AvailabilityBand {
        HIGH, MEDIUM, LOW
    }

    private boolean isFailureDowntime(DowntimeEvent event) {
        return event.getType() == DowntimeType.UNPLANNED || event.getType() == DowntimeType.EMERGENCY;
    }

    private List<DowntimeSlice> reliabilityDowntimes(List<DowntimeEvent> downtimes,
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
                .filter(this::isFailureDowntime)
                .forEach(event -> {
                    DowntimeSlice slice = sliceToPeriod(event, periodStart, periodEnd);
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
                .filter(this::isRepairWorkOrder)
                .filter(workOrder -> workOrder.getId() == null || !representedWorkOrderIds.contains(workOrder.getId()))
                .forEach(workOrder -> {
                    DowntimeSlice slice = sliceToPeriod(
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
                .filter(this::isReliabilityRepairRequest)
                .filter(request -> request.getId() == null || !representedRepairRequestIds.contains(request.getId()))
                .map(request -> sliceToPeriod(
                        request.getDetectedAt(),
                        request.getActualCompletionAt(),
                        request.getStatus() == RequestStatus.COMPLETED || request.getStatus() == RequestStatus.CLOSED,
                        periodStart,
                        periodEnd))
                .filter(slice -> slice.durationMinutes() > 0)
                .forEach(result::add);

        return result;
    }

    private boolean isRepairWorkOrder(WorkOrder workOrder) {
        return workOrder.getWorkType() == WorkType.REPAIR
                && workOrder.getStartedAt() != null
                && workOrder.getStatus() != WorkOrderStatus.CANCELLED;
    }

    private boolean isReliabilityRepairRequest(RepairRequest request) {
        return request.getDetectedAt() != null
                && request.getStatus() != RequestStatus.DRAFT
                && request.getStatus() != RequestStatus.REJECTED
                && request.getStatus() != RequestStatus.CANCELLED;
    }

    private long mergedDowntimeMinutes(List<DowntimeSlice> slices) {
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

    private DowntimeSlice sliceToPeriod(DowntimeEvent event, Instant periodStart, Instant periodEnd) {
        if (event.getStartAt() == null) {
            return DowntimeSlice.empty();
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

        return sliceToPeriod(event.getStartAt(), eventEnd, completed, periodStart, periodEnd);
    }

    private DowntimeSlice sliceToPeriod(Instant start,
                                        Instant end,
                                        boolean completed,
                                        Instant periodStart,
                                        Instant periodEnd) {
        if (start == null) {
            return DowntimeSlice.empty();
        }
        Instant effectiveEnd = end != null ? end : periodEnd;
        Instant overlapStart = start.isAfter(periodStart) ? start : periodStart;
        Instant overlapEnd = effectiveEnd.isBefore(periodEnd) ? effectiveEnd : periodEnd;
        if (!overlapEnd.isAfter(overlapStart)) {
            return DowntimeSlice.empty();
        }
        return new DowntimeSlice(overlapStart, overlapEnd,
                Duration.between(overlapStart, overlapEnd).toMinutes(), completed);
    }

    private record DowntimeSlice(Instant start, Instant end, long durationMinutes, boolean completed) {
        private static DowntimeSlice empty() {
            Instant epoch = Instant.EPOCH;
            return new DowntimeSlice(epoch, epoch, 0, false);
        }
    }
}

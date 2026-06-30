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

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReliabilityPassportService {

    private static final EnumSet<DefectStatus> OPEN_DEFECT_STATUSES =
            EnumSet.of(DefectStatus.OPEN, DefectStatus.IN_ANALYSIS, DefectStatus.IN_PROGRESS);
    private static final List<String> NUMERIC_SORT_FIELDS = List.of(
            "totalDefects",
            "openDefects",
            "totalDowntimeEvents",
            "totalDowntimeMinutes",
            "mtbfHours",
            "mttrHours",
            "availability",
            "availabilityPct"
    );

    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final DowntimeEventRepository downtimeRepository;
    private final WorkOrderRepository workOrderRepository;
    private final RepairRequestRepository repairRequestRepository;
    private final MetricExplanationService metricExplanationService;

    @Transactional(readOnly = true)
    public Page<ReliabilityPassport> list(UUID equipmentId, String search, String availability, int page, int size) {
        return list(equipmentId, search, availability, page, size, null, "asc");
    }

    @Transactional(readOnly = true)
    public Page<ReliabilityPassport> list(UUID equipmentId,
                                          String search,
                                          String availability,
                                          int page,
                                          int size,
                                          String sortBy,
                                          String sortDir) {
        return list(equipmentId, search, availability, page, size, sortBy, sortDir, null);
    }

    @Transactional(readOnly = true)
    public Page<ReliabilityPassport> list(UUID equipmentId,
                                          String search,
                                          String availability,
                                          int page,
                                          int size,
                                          String sortBy,
                                          String sortDir,
                                          String lang) {
        AvailabilityBand availabilityFilter = parseAvailabilityFilter(availability);
        String searchPattern = search == null || search.isBlank()
                ? null
                : "%" + search.toLowerCase() + "%";

        if (availabilityFilter != null || isNumericSort(sortBy)) {
            List<Equipment> equipmentList = equipmentRepository.searchAllForPassport(equipmentId, searchPattern);
            if (equipmentList.isEmpty()) {
                return PaginationUtils.page(List.of(), page, size);
            }

            List<ReliabilityPassport> filteredPassports = buildPassports(equipmentList, lang).stream()
                    .filter(passport -> availabilityFilter == null || bandOf(passport.availabilityPct()) == availabilityFilter)
                    .toList();
            if (isNumericSort(sortBy)) {
                filteredPassports = filteredPassports.stream()
                        .sorted(passportComparator(sortBy, sortDir))
                        .toList();
            }
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

        List<ReliabilityPassport> passports = buildPassports(equipmentPage.getContent(), lang);

        return new PageImpl<>(passports, equipmentPage.getPageable(), equipmentPage.getTotalElements());
    }

    private boolean isNumericSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return false;
        }
        return NUMERIC_SORT_FIELDS.contains(sortBy.trim());
    }

    private Comparator<ReliabilityPassport> passportComparator(String sortBy, String sortDir) {
        Comparator<ReliabilityPassport> comparator = switch (sortBy.trim()) {
            case "totalDefects" -> Comparator.comparingInt(ReliabilityPassport::totalDefects);
            case "openDefects" -> Comparator.comparingInt(ReliabilityPassport::openDefects);
            case "totalDowntimeEvents" -> Comparator.comparingInt(ReliabilityPassport::totalDowntimeEvents);
            case "totalDowntimeMinutes" -> Comparator.comparingLong(ReliabilityPassport::totalDowntimeMinutes);
            case "mtbfHours" -> Comparator.comparing(
                    ReliabilityPassport::mtbfHours,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "mttrHours" -> Comparator.comparing(
                    ReliabilityPassport::mttrHours,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "availability", "availabilityPct" -> Comparator.comparingDouble(ReliabilityPassport::availability);
            default -> throw RestException.badRequest("Unsupported reliability passport sort: " + sortBy);
        };
        return "desc".equalsIgnoreCase(sortDir) ? comparator.reversed() : comparator;
    }

    private List<ReliabilityPassport> buildPassports(List<Equipment> equipmentList) {
        return buildPassports(equipmentList, null);
    }

    private List<ReliabilityPassport> buildPassports(List<Equipment> equipmentList, String lang) {
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
                        now,
                        lang))
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
        return passport(equipmentId, null);
    }

    @Transactional(readOnly = true)
    public ReliabilityPassport passport(UUID equipmentId, String lang) {
        Equipment equipment = equipmentRepository.findByIdAndIsDeletedFalse(equipmentId)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + equipmentId));

        List<Defect> defects = defectRepository.findAllByEquipmentIdAndIsDeletedFalse(equipmentId);
        List<DowntimeEvent> downtimes = downtimeRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(equipmentId);
        List<WorkOrder> workOrders = workOrderRepository.search(null, null, equipmentId);
        List<RepairRequest> repairRequests = repairRequestRepository.search(null, null, equipmentId);

        return buildPassport(equipment, defects, downtimes, workOrders, repairRequests, Instant.now(), lang);
    }

    private ReliabilityPassport buildPassport(Equipment equipment,
                                              List<Defect> defects,
                                              List<DowntimeEvent> downtimes,
                                              List<WorkOrder> workOrders,
                                              List<RepairRequest> repairRequests,
                                              Instant now) {
        return buildPassport(equipment, defects, downtimes, workOrders, repairRequests, now, null);
    }

    private ReliabilityPassport buildPassport(Equipment equipment,
                                              List<Defect> defects,
                                              List<DowntimeEvent> downtimes,
                                              List<WorkOrder> workOrders,
                                              List<RepairRequest> repairRequests,
                                              Instant now,
                                              String lang) {
        List<Defect> activeDefects = defects.stream()
                .filter(d -> d.getStatus() != DefectStatus.CANCELLED)
                .toList();
        int openDefects = (int) activeDefects.stream()
                .filter(d -> OPEN_DEFECT_STATUSES.contains(d.getStatus()))
                .count();

        ReliabilityDowntimeCalculator.EquipmentReliability reliability =
                ReliabilityDowntimeCalculator.calculate(equipment, downtimes, workOrders, repairRequests, now);
        double downtimeHours = reliability.totalDowntimeMinutes() / 60.0;

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
                .sorted(Comparator.comparingInt(TopCause::count).reversed()
                        .thenComparing(cause -> "UNKNOWN".equalsIgnoreCase(cause.cause()) ? 1 : 0)
                        .thenComparing(TopCause::cause, String.CASE_INSENSITIVE_ORDER))
                .limit(10)
                .toList();

        double availabilityPercent = availabilityFromMtbfMttr(reliability);

        return new ReliabilityPassport(
                equipment.getId(),
                equipment.getCode(),
                equipment.getName(),
                activeDefects.size(),
                openDefects,
                reliability.failureEvents(),
                reliability.totalDowntimeMinutes(),
                metricExplanationService.formatDurationMinutes(lang, reliability.totalDowntimeMinutes()),
                reliability.mtbfHours(),
                metricExplanationService.formatDurationHours(lang, reliability.mtbfHours()),
                reliability.mttrHours(),
                metricExplanationService.formatDurationHours(lang, reliability.mttrHours()),
                availabilityPercent,
                availabilityPercent,
                topCauses,
                now,
                metricExplanationService.availability(
                        lang,
                        reliability.observedHours(),
                        downtimeHours,
                        reliability.operatingHours(),
                        availabilityPercent
                )
        );
    }

    private double availabilityPct(Equipment equipment,
                                   List<DowntimeEvent> downtimes,
                                   List<WorkOrder> workOrders,
                                   List<RepairRequest> repairRequests,
                                   Instant now) {
        return availabilityFromMtbfMttr(ReliabilityDowntimeCalculator.calculate(
                equipment, downtimes, workOrders, repairRequests, now));
    }

    private static double availabilityFromMtbfMttr(ReliabilityDowntimeCalculator.EquipmentReliability reliability) {
        if (reliability.mtbfHours() != null
                && reliability.mtbfHours() > 0
                && reliability.mttrHours() != null
                && reliability.mttrHours() >= 0) {
            double denominator = reliability.mtbfHours() + reliability.mttrHours();
            if (denominator > 0) {
                return clampPercent(reliability.mtbfHours() / denominator * 100.0);
            }
        }
        if (reliability.failureEvents() == 0 && reliability.mtbfHours() == null && reliability.mttrHours() == null) {
            return 100.0;
        }
        return clampPercent(reliability.availabilityPct());
    }

    private static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.min(100.0, Math.max(0.0, value));
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

}

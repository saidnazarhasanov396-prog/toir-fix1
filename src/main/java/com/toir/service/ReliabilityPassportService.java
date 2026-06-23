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

        ReliabilityDowntimeCalculator.EquipmentReliability reliability =
                ReliabilityDowntimeCalculator.calculate(equipment, downtimes, workOrders, repairRequests, now);

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
                reliability.failureEvents(),
                reliability.totalDowntimeMinutes(),
                reliability.mtbfHours(),
                reliability.mttrHours(),
                reliability.availabilityPct(),
                topCauses,
                now
        );
    }

    private double availabilityPct(Equipment equipment,
                                   List<DowntimeEvent> downtimes,
                                   List<WorkOrder> workOrders,
                                   List<RepairRequest> repairRequests,
                                   Instant now) {
        return ReliabilityDowntimeCalculator.calculate(equipment, downtimes, workOrders, repairRequests, now)
                .availabilityPct();
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

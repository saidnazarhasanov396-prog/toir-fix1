package com.toir.service;

import com.toir.controller.ReliabilityPassportController.ReliabilityPassport;
import com.toir.controller.ReliabilityPassportController.ReliabilityPassportStats;
import com.toir.controller.ReliabilityPassportController.TopCause;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.DefectStatus;
import com.toir.enums.DowntimeType;
import com.toir.exception.RestException;
import com.toir.repository.DowntimeEventRepository;
import com.toir.repository.defects.DefectRepository;
import com.toir.repository.equipment.EquipmentRepository;
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
import java.util.List;
import java.util.Map;
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

    @Transactional(readOnly = true)
    public Page<ReliabilityPassport> list(UUID equipmentId, String search, int page, int size) {
        String searchPattern = search == null || search.isBlank()
                ? null
                : "%" + search.toLowerCase() + "%";

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

        Map<UUID, List<Defect>> defectsByEquipment = defectRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(ids)
                .stream()
                .collect(Collectors.groupingBy(Defect::getEquipmentId));

        Map<UUID, List<DowntimeEvent>> downtimesByEquipment = downtimeRepository
                .findAllByEquipmentIdInAndIsDeletedFalse(ids)
                .stream()
                .collect(Collectors.groupingBy(DowntimeEvent::getEquipmentId));

        Instant now = Instant.now();
        List<ReliabilityPassport> passports = equipmentPage.getContent().stream()
                .map(eq -> buildPassport(
                        eq,
                        defectsByEquipment.getOrDefault(eq.getId(), List.of()),
                        downtimesByEquipment.getOrDefault(eq.getId(), List.of()),
                        now))
                .toList();

        return new PageImpl<>(passports, equipmentPage.getPageable(), equipmentPage.getTotalElements());
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

        Instant now = Instant.now();
        int total = 0;
        int high = 0;
        int medium = 0;
        int low = 0;
        for (Equipment equipment : equipmentList) {
            double availabilityPct = availabilityPct(
                    equipment,
                    downtimesByEquipment.getOrDefault(equipment.getId(), List.of()),
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

        return buildPassport(equipment, defects, downtimes, Instant.now());
    }

    private ReliabilityPassport buildPassport(Equipment equipment,
                                              List<Defect> defects,
                                              List<DowntimeEvent> downtimes,
                                              Instant now) {
        List<Defect> activeDefects = defects.stream()
                .filter(d -> d.getStatus() != DefectStatus.CANCELLED)
                .toList();
        int openDefects = (int) activeDefects.stream()
                .filter(d -> OPEN_DEFECT_STATUSES.contains(d.getStatus()))
                .count();

        Instant periodStart = analysisPeriodStart(equipment, now);
        List<DowntimeSlice> failureDowntimes = downtimes.stream()
                .filter(this::isFailureDowntime)
                .map(event -> sliceToPeriod(event, periodStart, now))
                .filter(slice -> slice.durationMinutes() > 0)
                .toList();
        long totalDowntimeMinutes = 0;
        long mttrDenominator = 0;
        long mttrSumMinutes = 0;
        for (DowntimeSlice slice : failureDowntimes) {
            long minutes = slice.durationMinutes();
            totalDowntimeMinutes += minutes;
            if (slice.completed()) {
                mttrSumMinutes += minutes;
                mttrDenominator++;
            }
        }

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

    private double availabilityPct(Equipment equipment, List<DowntimeEvent> downtimes, Instant now) {
        Instant periodStart = analysisPeriodStart(equipment, now);
        double observedHours = Duration.between(periodStart, now).toMinutes() / 60.0;
        long downtimeMinutes = downtimes.stream()
                .filter(this::isFailureDowntime)
                .map(event -> sliceToPeriod(event, periodStart, now))
                .mapToLong(DowntimeSlice::durationMinutes)
                .sum();
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

    private DowntimeSlice sliceToPeriod(DowntimeEvent event, Instant periodStart, Instant periodEnd) {
        if (event.getStartAt() == null) {
            return new DowntimeSlice(0, false);
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

        Instant overlapStart = event.getStartAt().isAfter(periodStart) ? event.getStartAt() : periodStart;
        Instant overlapEnd = eventEnd.isBefore(periodEnd) ? eventEnd : periodEnd;
        if (!overlapEnd.isAfter(overlapStart)) {
            return new DowntimeSlice(0, completed);
        }
        return new DowntimeSlice(Duration.between(overlapStart, overlapEnd).toMinutes(), completed);
    }

    private record DowntimeSlice(long durationMinutes, boolean completed) {}
}

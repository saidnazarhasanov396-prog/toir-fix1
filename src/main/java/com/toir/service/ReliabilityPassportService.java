package com.toir.service;

import com.toir.controller.ReliabilityPassportController.ReliabilityPassport;
import com.toir.controller.ReliabilityPassportController.TopCause;
import com.toir.entity.DowntimeEvent;
import com.toir.entity.defects.Defect;
import com.toir.entity.equipment.Equipment;
import com.toir.enums.DefectStatus;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReliabilityPassportService {

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
        int openDefects = (int) defects.stream().filter(d -> d.getStatus() != DefectStatus.CLOSED).count();

        long totalDowntimeMinutes = 0;
        long mttrDenominator = 0;
        long mttrSumMinutes = 0;
        Instant firstEvent = null;
        Instant lastEvent = null;
        for (DowntimeEvent ev : downtimes) {
            long minutes = eventDurationMinutes(ev);
            totalDowntimeMinutes += minutes;
            if (minutes > 0) {
                mttrSumMinutes += minutes;
                mttrDenominator++;
            }
            if (firstEvent == null || ev.getStartAt().isBefore(firstEvent)) firstEvent = ev.getStartAt();
            if (lastEvent == null || ev.getStartAt().isAfter(lastEvent)) lastEvent = ev.getStartAt();
        }

        Double mttrHours = mttrDenominator > 0 ? (mttrSumMinutes / 60.0) / mttrDenominator : null;
        Double mtbfHours = null;
        if (downtimes.size() >= 2 && firstEvent != null && lastEvent != null) {
            long spanHours = Duration.between(firstEvent, lastEvent).toHours();
            long uptimeHours = Math.max(spanHours - (totalDowntimeMinutes / 60), 0);
            mtbfHours = uptimeHours / (double) downtimes.size();
        }

        Instant horizon = now.minusSeconds(60L * 60 * 24 * 365);
        long periodHours = Duration.between(horizon, now).toHours();
        long downtimeLastYearMinutes = downtimes.stream()
                .filter(ev -> ev.getStartAt().isAfter(horizon))
                .mapToLong(this::eventDurationMinutes)
                .sum();
        double availabilityPct = periodHours > 0
                ? Math.max(0, 100.0 - (downtimeLastYearMinutes / 60.0) / periodHours * 100.0)
                : 100.0;

        Map<String, Integer> causes = new HashMap<>();
        for (Defect d : defects) {
            String key = d.getRootCause() != null && !d.getRootCause().isBlank()
                    ? d.getRootCause()
                    : (d.getFailureReason() != null ? d.getFailureReason() : "UNKNOWN");
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
                defects.size(),
                openDefects,
                downtimes.size(),
                totalDowntimeMinutes,
                mtbfHours,
                mttrHours,
                availabilityPct,
                topCauses,
                now
        );
    }

    private long eventDurationMinutes(DowntimeEvent event) {
        if (event.getDurationMinutes() != null) return event.getDurationMinutes();
        if (event.getEndAt() != null) return Duration.between(event.getStartAt(), event.getEndAt()).toMinutes();
        return 0L;
    }
}

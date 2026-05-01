package com.toir.controller;

import com.toir.exception.RestException;
import com.toir.entity.Defect;
import com.toir.repository.DefectRepository;
import com.toir.enums.DefectStatus;
import com.toir.entity.DowntimeEvent;
import com.toir.repository.DowntimeEventRepository;
import com.toir.entity.Equipment;
import com.toir.repository.EquipmentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/equipment")
@Tag(name = "equipment-reliability-passport")
public class ReliabilityPassportController {

    private final EquipmentRepository equipmentRepository;
    private final DefectRepository defectRepository;
    private final DowntimeEventRepository downtimeRepository;

    public ReliabilityPassportController(EquipmentRepository equipmentRepository,
                                         DefectRepository defectRepository,
                                         DowntimeEventRepository downtimeRepository) {
        this.equipmentRepository = equipmentRepository;
        this.defectRepository = defectRepository;
        this.downtimeRepository = downtimeRepository;
    }

    public record TopCause(String cause, int count) {}

    public record ReliabilityPassport(
            UUID equipmentId,
            String equipmentCode,
            String equipmentName,
            int totalDefects,
            int openDefects,
            int totalDowntimeEvents,
            long totalDowntimeMinutes,
            Double mtbfHours,
            Double mttrHours,
            double availabilityPct,
            List<TopCause> topRootCauses,
            Instant generatedAt
    ) {}

    @GetMapping("/{id}/reliability-passport")
    public ReliabilityPassport passport(@PathVariable UUID id) {
        Equipment eq = equipmentRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> RestException.notFound("Equipment not found: " + id));

        List<Defect> defects = com.toir.util.UpdatedAtSorter.descending(defectRepository.findAllByEquipmentIdAndIsDeletedFalse(id));
        List<DowntimeEvent> downtimes = com.toir.util.UpdatedAtSorter.descending(downtimeRepository.findAllByEquipmentIdAndIsDeletedFalseOrderByStartAtDesc(id));

        int openDefects = (int) defects.stream().filter(d -> d.getStatus() != DefectStatus.CLOSED).count();

        long totalDowntimeMinutes = 0;
        long mttrDenominator = 0;
        long mttrSumMinutes = 0;
        Instant firstEvent = null;
        Instant lastEvent = null;
        for (DowntimeEvent ev : downtimes) {
            long minutes;
            if (ev.getDurationMinutes() != null) {
                minutes = ev.getDurationMinutes();
            } else if (ev.getEndAt() != null) {
                minutes = Duration.between(ev.getStartAt(), ev.getEndAt()).toMinutes();
            } else {
                minutes = 0;
            }
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

        Instant horizon = Instant.now().minusSeconds(60L * 60 * 24 * 365);
        long periodHours = Duration.between(horizon, Instant.now()).toHours();
        long downtimeLastYearMinutes = downtimes.stream()
                .filter(ev -> ev.getStartAt().isAfter(horizon))
                .mapToLong(ev -> {
                    if (ev.getDurationMinutes() != null) return ev.getDurationMinutes();
                    if (ev.getEndAt() != null) return Duration.between(ev.getStartAt(), ev.getEndAt()).toMinutes();
                    return 0L;
                }).sum();
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
                eq.getId(), eq.getCode(), eq.getName(),
                defects.size(), openDefects,
                downtimes.size(), totalDowntimeMinutes,
                mtbfHours, mttrHours, availabilityPct,
                topCauses, Instant.now()
        );
    }
}

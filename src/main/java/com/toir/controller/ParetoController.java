package com.toir.controller;

import com.toir.entity.Defect;
import com.toir.repository.DefectRepository;
import com.toir.entity.DowntimeEvent;
import com.toir.repository.DowntimeEventRepository;
import com.toir.entity.WorkOrder;
import com.toir.repository.WorkOrderRepository;
import com.toir.enums.WorkOrderStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "analytics-pareto")
public class ParetoController {

    private final DefectRepository defectRepository;
    private final DowntimeEventRepository downtimeRepository;
    private final WorkOrderRepository workOrderRepository;

    public ParetoController(DefectRepository defectRepository,
                            DowntimeEventRepository downtimeRepository,
                            WorkOrderRepository workOrderRepository) {
        this.defectRepository = defectRepository;
        this.downtimeRepository = downtimeRepository;
        this.workOrderRepository = workOrderRepository;
    }

    public record ParetoItem(
            String key,
            double value,
            double cumulativePct
    ) {}

    public record TopEquipmentItem(
            UUID equipmentId,
            int failures,
            long totalDowntimeMinutes,
            int openWorkOrders
    ) {}

    @GetMapping("/pareto/downtime-causes")
    public List<ParetoItem> downtimeCauses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now();
        List<DowntimeEvent> events = downtimeRepository.findAll().stream()
                .filter(e -> !e.getStartAt().isBefore(start) && !e.getStartAt().isAfter(end))
                .toList();
        Map<String, Double> byType = new HashMap<>();
        for (DowntimeEvent ev : events) {
            long minutes;
            if (ev.getDurationMinutes() != null) minutes = ev.getDurationMinutes();
            else if (ev.getEndAt() != null) minutes = Duration.between(ev.getStartAt(), ev.getEndAt()).toMinutes();
            else continue;
            String key = ev.getType() != null ? ev.getType().name() : "UNKNOWN";
            byType.merge(key, (double) minutes, Double::sum);
        }
        return pareto(byType);
    }

    @GetMapping("/pareto/defect-root-causes")
    public List<ParetoItem> defectRootCauses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now();
        List<Defect> defects = defectRepository.findAll().stream()
                .filter(d -> !d.getDetectedAt().isBefore(start) && !d.getDetectedAt().isAfter(end))
                .toList();
        Map<String, Double> byCause = new HashMap<>();
        for (Defect d : defects) {
            String key = d.getRootCause() != null && !d.getRootCause().isBlank()
                    ? d.getRootCause()
                    : (d.getFailureReason() != null && !d.getFailureReason().isBlank() ? d.getFailureReason() : "UNKNOWN");
            byCause.merge(key, 1.0, Double::sum);
        }
        return pareto(byCause);
    }

    @GetMapping("/top-problem-equipment")
    public List<TopEquipmentItem> topProblemEquipment(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now();

        Map<UUID, int[]> failuresByEq = new HashMap<>();
        for (Defect d : defectRepository.findAll()) {
            if (d.getDetectedAt().isBefore(start) || d.getDetectedAt().isAfter(end)) continue;
            failuresByEq.computeIfAbsent(d.getEquipmentId(), k -> new int[]{0})[0]++;
        }

        Map<UUID, Long> downtimeByEq = new HashMap<>();
        for (DowntimeEvent ev : downtimeRepository.findAll()) {
            if (ev.getStartAt().isBefore(start) || ev.getStartAt().isAfter(end)) continue;
            long minutes;
            if (ev.getDurationMinutes() != null) minutes = ev.getDurationMinutes();
            else if (ev.getEndAt() != null) minutes = Duration.between(ev.getStartAt(), ev.getEndAt()).toMinutes();
            else continue;
            downtimeByEq.merge(ev.getEquipmentId(), minutes, Long::sum);
        }

        Map<UUID, Integer> openWorkOrdersByEq = new HashMap<>();
        for (WorkOrder wo : workOrderRepository.findAll()) {
            if (wo.getStatus() == WorkOrderStatus.COMPLETED || wo.getStatus() == WorkOrderStatus.CANCELLED) continue;
            if (wo.getEquipmentId() == null) continue;
            openWorkOrdersByEq.merge(wo.getEquipmentId(), 1, Integer::sum);
        }

        List<TopEquipmentItem> all = new ArrayList<>();
        java.util.Set<UUID> keys = new java.util.HashSet<>();
        keys.addAll(failuresByEq.keySet());
        keys.addAll(downtimeByEq.keySet());
        keys.addAll(openWorkOrdersByEq.keySet());
        for (UUID eqId : keys) {
            int failures = failuresByEq.getOrDefault(eqId, new int[]{0})[0];
            long downtime = downtimeByEq.getOrDefault(eqId, 0L);
            int openWo = openWorkOrdersByEq.getOrDefault(eqId, 0);
            all.add(new TopEquipmentItem(eqId, failures, downtime, openWo));
        }

        int safeLimit = Math.max(Math.min(limit, 100), 1);
        return all.stream()
                .sorted(Comparator
                        .comparingInt(TopEquipmentItem::failures).reversed()
                        .thenComparingLong((TopEquipmentItem i) -> -i.totalDowntimeMinutes()))
                .limit(safeLimit)
                .toList();
    }

    private List<ParetoItem> pareto(Map<String, Double> raw) {
        double total = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Map.Entry<String, Double>> sorted = raw.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();
        List<ParetoItem> result = new ArrayList<>();
        double cumulative = 0;
        for (Map.Entry<String, Double> e : sorted) {
            cumulative += e.getValue();
            double pct = total > 0 ? (cumulative / total) * 100.0 : 0;
            result.add(new ParetoItem(e.getKey(), e.getValue(), pct));
        }
        return result;
    }
}

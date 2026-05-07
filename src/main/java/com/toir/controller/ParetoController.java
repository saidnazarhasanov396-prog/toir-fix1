package com.toir.controller;

import com.toir.entity.defects.Defect;
import com.toir.service.ParetoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "analytics-pareto")
@RequiredArgsConstructor
public class ParetoController {

    private final ParetoService paretoService;

    public record ParetoItem(
            String key,
            double value,
            double cumulativePct
    ) {}

    public record TopEquipmentItem(
            UUID equipmentId,
            String equipmentName,
            int failures,
            long totalDowntimeMinutes,
            int openWorkOrders
    ) {}

    @GetMapping("/pareto/downtime-causes")
    public ResponseEntity<Page<ParetoItem>> downtimeCauses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(paretoService.downtimeCauses(from, to, page, size));
    }

    @GetMapping("/pareto/defect-root-causes")
    public ResponseEntity<Page<ParetoItem>> defectRootCauses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(paretoService.defectRootCauses(from, to, page, size));
    }

    @GetMapping("/top-problem-equipment")
    public ResponseEntity<Page<TopEquipmentItem>> topProblemEquipment(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(paretoService.topProblemEquipment(limit, from, to, page, size));
    }
}

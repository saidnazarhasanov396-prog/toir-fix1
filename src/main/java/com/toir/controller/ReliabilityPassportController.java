package com.toir.controller;

import com.toir.entity.defects.Defect;
import com.toir.service.ReliabilityPassportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment")
@Tag(name = "equipment-reliability-passport")
@RequiredArgsConstructor
public class ReliabilityPassportController {

    private final ReliabilityPassportService reliabilityPassportService;

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
    public ResponseEntity<ReliabilityPassport> passport(@PathVariable UUID id) {
        return ResponseEntity.ok(reliabilityPassportService.passport(id));
    }
}

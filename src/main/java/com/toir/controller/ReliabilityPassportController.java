package com.toir.controller;

import com.toir.service.ReliabilityPassportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/reliability-passport")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<ReliabilityPassport>> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(reliabilityPassportService.list(equipmentId, search, page, size));
    }
}

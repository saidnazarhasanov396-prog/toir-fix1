package com.toir.controller;

import com.toir.dto.analytics.MetricExplanationDto;
import com.toir.service.ReliabilityPassportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
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
            @Schema(description = "Equipment identifier.")
            UUID equipmentId,
            @Schema(description = "Equipment registry code")
            String equipmentCode,
            @Schema(description = "Equipment registry name")
            String equipmentName,
            @Schema(description = "Lifetime defect records excluding cancelled defects")
            int totalDefects,
            @Schema(description = "Defects currently in OPEN, IN_ANALYSIS, or IN_PROGRESS status")
            int openDefects,
            @Schema(description = "Reliability-impacting events in the analysis period: explicit failure downtime, "
                    + "or repair work order/request intervals when explicit downtime is absent")
            int totalDowntimeEvents,
            @Schema(description = "Deduplicated unavailable minutes from failure downtime and repair intervals")
            long totalDowntimeMinutes,
            @Schema(description = "Localized human-readable total downtime duration")
            String totalDowntimeDuration,
            @Schema(description = "Operating hours in the analysis period divided by failure downtime event count")
            Double mtbfHours,
            @Schema(description = "Localized human-readable MTBF duration")
            String mtbfDuration,
            @Schema(description = "Average duration in hours of completed unplanned or emergency downtime events")
            Double mttrHours,
            @Schema(description = "Localized human-readable MTTR duration")
            String mttrDuration,
            @Schema(description = "Availability percent calculated from MTBF and MTTR, capped at 100")
            double availability,
            @Schema(description = "Backward-compatible availability percent alias")
            double availabilityPct,
            @Schema(description = "Top causes from non-cancelled defect rootCause/failureReason fields")
            List<TopCause> topRootCauses,
            @Schema(description = "Time when this read-only passport was calculated")
            Instant generatedAt,
            @Schema(description = "Localized explanation of the availability calculation.")
            MetricExplanationDto explanation
    ) {
        public ReliabilityPassport(
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
        ) {
            this(equipmentId, equipmentCode, equipmentName, totalDefects, openDefects, totalDowntimeEvents,
                    totalDowntimeMinutes, null, mtbfHours, null, mttrHours, null, availabilityPct, availabilityPct,
                    topRootCauses, generatedAt, null);
        }
    }

    public record ReliabilityPassportStats(
            int total,
            int highAvailability,
            int mediumAvailability,
            int lowAvailability
    ) {}

    @GetMapping("/reliability-passport")
    @Operation(summary = "List calculated reliability passports",
            description = "Metrics use the period from operation start, commissioning, or equipment creation until now. "
                    + "UNPLANNED/EMERGENCY downtime is preferred; actual repair work-order or repair-request intervals "
                    + "are used as fallbacks and overlapping intervals are counted once.")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<Page<ReliabilityPassport>> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String availability,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        String responseLang = requestedLanguage(lang, acceptLanguage);
        if (sortBy == null || sortBy.isBlank()) {
            if (responseLang == null) {
                return ResponseEntity.ok(reliabilityPassportService.list(equipmentId, search, availability, page, size));
            }
            return ResponseEntity.ok(reliabilityPassportService.list(
                    equipmentId, search, availability, page, size, null, "asc", responseLang));
        }
        if (responseLang == null) {
            return ResponseEntity.ok(reliabilityPassportService.list(
                    equipmentId, search, availability, page, size, sortBy, sortDir));
        }
        return ResponseEntity.ok(reliabilityPassportService.list(
                equipmentId, search, availability, page, size, sortBy, sortDir, responseLang));
    }

    @GetMapping("/reliability-passports/stats")
    @Operation(summary = "Count equipment by calculated availability band",
            description = "Uses the same analysis period and downtime rules as the reliability passport list.")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('EQUIPMENT_READ')")
    public ResponseEntity<ReliabilityPassportStats> stats(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        return ResponseEntity.ok(reliabilityPassportService.stats(equipmentId, search, availability));
    }

    private String requestedLanguage(String lang, String acceptLanguage) {
        if (lang != null && !lang.isBlank()) {
            return lang;
        }
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            return acceptLanguage;
        }
        return null;
    }
}

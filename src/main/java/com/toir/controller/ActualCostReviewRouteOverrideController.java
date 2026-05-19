package com.toir.controller;

import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideCreateRequest;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideDto;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideResponseDto;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.ActualCostReviewRouteOverrideService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets/actual-costs/review-route-overrides")
@Tag(name = "actual-cost-route-overrides")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class ActualCostReviewRouteOverrideController {

    private final ActualCostReviewRouteOverrideService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_READ')")
    public ResponseEntity<Page<ActualCostReviewRouteOverrideResponseDto>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ResponseEntity.ok(PaginationUtils.page(service.findActive(), page, size)); }

    @GetMapping("/by-actual-cost/{actualCostId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_READ')")
    public ResponseEntity<Page<ActualCostReviewRouteOverrideResponseDto>> byActualCost(@PathVariable UUID actualCostId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByActualCost(actualCostId), page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_APPLY')")
    public ResponseEntity<ActualCostReviewRouteOverrideResponseDto> apply(
            @Valid @RequestBody ActualCostReviewRouteOverrideCreateRequest r) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.apply(r));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_CLEAR')")
    public ResponseEntity<ActualCostReviewRouteOverrideDto> deactivate(@PathVariable UUID id, @RequestParam UUID userId, @RequestParam String comment) {
        return ResponseEntity.ok(service.deactivate(id, userId, comment));
    }
}

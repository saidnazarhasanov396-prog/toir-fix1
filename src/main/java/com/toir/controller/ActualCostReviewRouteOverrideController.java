package com.toir.controller;

import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideCreateRequest;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideDto;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideResponseDto;
import com.toir.service.ActualCostReviewRouteOverrideService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets/actual-costs/review-route-overrides")
@Tag(name = "actual-cost-route-overrides")
@RequiredArgsConstructor
public class ActualCostReviewRouteOverrideController {

    private final ActualCostReviewRouteOverrideService service;

    @GetMapping
    public ResponseEntity<Page<ActualCostReviewRouteOverrideDto>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ResponseEntity.ok(PaginationUtils.page(service.findActive(), page, size)); }

    @GetMapping("/by-actual-cost/{actualCostId}")
    public ResponseEntity<Page<ActualCostReviewRouteOverrideDto>> byActualCost(@PathVariable UUID actualCostId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByActualCost(actualCostId), page, size));
    }

    @PostMapping
    public ResponseEntity<ActualCostReviewRouteOverrideResponseDto> apply(
            @Valid @RequestBody ActualCostReviewRouteOverrideCreateRequest r) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.apply(r));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ActualCostReviewRouteOverrideDto> deactivate(@PathVariable UUID id, @RequestParam UUID userId, @RequestParam String comment) {
        return ResponseEntity.ok(service.deactivate(id, userId, comment));
    }
}

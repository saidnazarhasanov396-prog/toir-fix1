package com.toir.actualcostrouteoverride;

import com.toir.actualcostrouteoverride.dto.ActualCostReviewRouteOverrideDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets/actual-costs/review-route-overrides")
@Tag(name = "actual-cost-route-overrides")
public class ActualCostReviewRouteOverrideController {

    private final ActualCostReviewRouteOverrideService service;

    public ActualCostReviewRouteOverrideController(ActualCostReviewRouteOverrideService service) {
        this.service = service;
    }

    @GetMapping
    public List<ActualCostReviewRouteOverrideDto> list() { return service.findActive(); }

    @GetMapping("/by-actual-cost/{actualCostId}")
    public List<ActualCostReviewRouteOverrideDto> byActualCost(@PathVariable UUID actualCostId) {
        return service.findByActualCost(actualCostId);
    }

    @PostMapping
    public ResponseEntity<ActualCostReviewRouteOverrideDto> apply(@Valid @RequestBody ActualCostReviewRouteOverrideDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(r));
    }

    @PostMapping("/{id}/deactivate")
    public ActualCostReviewRouteOverrideDto deactivate(@PathVariable UUID id, @RequestParam UUID userId, @RequestParam String comment) {
        return service.deactivate(id, userId, comment);
    }
}

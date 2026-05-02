package com.toir.controller;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideDto;
import com.toir.service.ActualCostReviewRouteOverrideService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/budgets/actual-costs/review-route-overrides")
@Tag(name = "actual-cost-route-overrides")
public class ActualCostReviewRouteOverrideController {

    private final ActualCostReviewRouteOverrideService service;

    public ActualCostReviewRouteOverrideController(ActualCostReviewRouteOverrideService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ActualCostReviewRouteOverrideDto>> list() { return ResponseEntity.ok(service.findActive()); }

    @GetMapping("/by-actual-cost/{actualCostId}")
    public ResponseEntity<List<ActualCostReviewRouteOverrideDto>> byActualCost(@PathVariable UUID actualCostId) {
        return ResponseEntity.ok(service.findByActualCost(actualCostId));
    }

    @PostMapping
    public ResponseEntity<ActualCostReviewRouteOverrideDto> apply(@Valid @RequestBody ActualCostReviewRouteOverrideDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(r));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ActualCostReviewRouteOverrideDto> deactivate(@PathVariable UUID id, @RequestParam UUID userId, @RequestParam String comment) {
        return ResponseEntity.ok(service.deactivate(id, userId, comment));
    }
}

package com.toir.controller;
import com.toir.dto.actualcost.ActualCostDto;
import com.toir.service.ActualCostService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/actual-costs")
@Tag(name = "actual-costs")
public class ActualCostController {

    private final ActualCostService service;

    public ActualCostController(ActualCostService service) { this.service = service; }

    @GetMapping("/pending")
    public ResponseEntity<List<ActualCostDto>> pending() { return ResponseEntity.ok(service.findPending()); }

    @GetMapping
    public ResponseEntity<List<ActualCostDto>> list(@RequestParam UUID workOrderId) {
        return ResponseEntity.ok(service.findByWorkOrder(workOrderId));
    }

    @PostMapping
    public ResponseEntity<ActualCostDto> create(@Valid @RequestBody ActualCostDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ActualCostDto> approve(@PathVariable UUID id, @RequestParam UUID reviewerId, @RequestParam(required = false) String comment) {
        return ResponseEntity.ok(service.review(id, true, reviewerId, comment));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ActualCostDto> reject(@PathVariable UUID id, @RequestParam UUID reviewerId, @RequestParam String comment) {
        return ResponseEntity.ok(service.review(id, false, reviewerId, comment));
    }
}

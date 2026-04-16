package com.toir.controller;
import com.toir.service.ActualCostService;

import com.toir.dto.actualcost.ActualCostDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/actual-costs")
@Tag(name = "actual-costs")
public class ActualCostController {

    private final ActualCostService service;

    public ActualCostController(ActualCostService service) { this.service = service; }

    @GetMapping("/pending")
    public List<ActualCostDto> pending() { return service.findPending(); }

    @GetMapping
    public List<ActualCostDto> list(@RequestParam UUID workOrderId) {
        return service.findByWorkOrder(workOrderId);
    }

    @PostMapping
    public ResponseEntity<ActualCostDto> create(@Valid @RequestBody ActualCostDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    public ActualCostDto approve(@PathVariable UUID id, @RequestParam UUID reviewerId, @RequestParam(required = false) String comment) {
        return service.review(id, true, reviewerId, comment);
    }

    @PostMapping("/{id}/reject")
    public ActualCostDto reject(@PathVariable UUID id, @RequestParam UUID reviewerId, @RequestParam String comment) {
        return service.review(id, false, reviewerId, comment);
    }
}

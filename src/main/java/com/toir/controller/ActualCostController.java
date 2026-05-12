package com.toir.controller;
import com.toir.dto.actualcost.ActualCostDto;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.ActualCostService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/actual-costs")
@Tag(name = "actual-costs")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class ActualCostController {

    private final ActualCostService service;

    @GetMapping("/pending")
    public ResponseEntity<Page<ActualCostDto>> pending(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ResponseEntity.ok(PaginationUtils.page(service.findPending(), page, size)); }

    @GetMapping
    public ResponseEntity<Page<ActualCostDto>> list(@RequestParam(required = false) UUID workOrderId,
                                                    @RequestParam(required = false) String search,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByFilters(workOrderId, search), page, size));
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

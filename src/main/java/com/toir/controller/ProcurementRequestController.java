package com.toir.controller;
import com.toir.entity.ProcurementRequestStatus;
import com.toir.service.ProcurementRequestService;

import com.toir.dto.procurement.ProcurementLineRequest;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.procurement.ProcurementRequestRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/procurement-requests")
@Tag(name = "procurement-requests")
public class ProcurementRequestController {

    private final ProcurementRequestService service;

    public ProcurementRequestController(ProcurementRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProcurementRequestDto> list(
            @RequestParam(required = false) ProcurementRequestStatus status,
            @RequestParam(required = false) UUID departmentId
    ) {
        return service.findAll(status, departmentId);
    }

    @GetMapping("/{id}")
    public ProcurementRequestDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<ProcurementRequestDto> create(@Valid @RequestBody ProcurementRequestRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/lines")
    public ProcurementRequestDto addLine(@PathVariable UUID id, @Valid @RequestBody ProcurementLineRequest r) {
        return service.addLine(id, r);
    }

    @PostMapping("/{id}/submit")
    public ProcurementRequestDto submit(@PathVariable UUID id) { return service.submit(id); }

    @PostMapping("/{id}/approve")
    public ProcurementRequestDto approve(@PathVariable UUID id) { return service.approve(id); }

    @PostMapping("/{id}/reject")
    public ProcurementRequestDto reject(@PathVariable UUID id, @RequestParam String reason) {
        return service.reject(id, reason);
    }

    @PostMapping("/{id}/ordered")
    public ProcurementRequestDto markOrdered(@PathVariable UUID id) { return service.markOrdered(id); }

    @PostMapping("/{id}/received")
    public ProcurementRequestDto markReceived(@PathVariable UUID id) { return service.markReceived(id); }

    @PostMapping("/{id}/cancel")
    public ProcurementRequestDto cancel(@PathVariable UUID id) { return service.cancel(id); }

    @PostMapping("/generate-from-low-stock")
    public List<ProcurementRequestDto> generateFromLowStock(@RequestParam(required = false) UUID warehouseId) {
        return service.generateFromLowStock(warehouseId);
    }
}

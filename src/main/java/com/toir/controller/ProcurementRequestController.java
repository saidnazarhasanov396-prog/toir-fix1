package com.toir.controller;
import com.toir.dto.procurement.ProcurementLineRequest;
import com.toir.dto.procurement.ProcurementRequestDto;
import com.toir.dto.procurement.ProcurementRequestRequest;
import com.toir.enums.ProcurementRequestStatus;
import com.toir.service.ProcurementRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/procurement-requests")
@Tag(name = "procurement-requests")
public class ProcurementRequestController {

    private final ProcurementRequestService service;

    public ProcurementRequestController(ProcurementRequestService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ProcurementRequestDto>> list(
            @RequestParam(required = false) ProcurementRequestStatus status,
            @RequestParam(required = false) UUID departmentId
    ) {
        return ResponseEntity.ok(service.findAll(status, departmentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcurementRequestDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<ProcurementRequestDto> create(@Valid @RequestBody ProcurementRequestRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/lines")
    public ResponseEntity<ProcurementRequestDto> addLine(@PathVariable UUID id, @Valid @RequestBody ProcurementLineRequest r) {
        return ResponseEntity.ok(service.addLine(id, r));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ProcurementRequestDto> submit(@PathVariable UUID id) { return ResponseEntity.ok(service.submit(id)); }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ProcurementRequestDto> approve(@PathVariable UUID id) { return ResponseEntity.ok(service.approve(id)); }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ProcurementRequestDto> reject(@PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(service.reject(id, reason));
    }

    @PostMapping("/{id}/ordered")
    public ResponseEntity<ProcurementRequestDto> markOrdered(@PathVariable UUID id) { return ResponseEntity.ok(service.markOrdered(id)); }

    @PostMapping("/{id}/received")
    public ResponseEntity<ProcurementRequestDto> markReceived(@PathVariable UUID id) { return ResponseEntity.ok(service.markReceived(id)); }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ProcurementRequestDto> cancel(@PathVariable UUID id) { return ResponseEntity.ok(service.cancel(id)); }

    @PostMapping("/generate-from-low-stock")
    public ResponseEntity<List<ProcurementRequestDto>> generateFromLowStock(@RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(service.generateFromLowStock(warehouseId));
    }
}

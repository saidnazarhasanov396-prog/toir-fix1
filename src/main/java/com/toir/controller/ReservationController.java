package com.toir.controller;
import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.reservation.ReservationRequest;
import com.toir.service.ReservationService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Page<ReservationDto>> list(@RequestParam UUID workOrderId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByWorkOrder(workOrderId), page, size));
    }

    @PostMapping
    public ResponseEntity<ReservationDto> reserve(@Valid @RequestBody ReservationRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reserve(r));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReservationDto> cancel(@PathVariable UUID id) { return ResponseEntity.ok(service.cancel(id)); }

    @PostMapping("/{id}/fulfill")
    public ResponseEntity<ReservationDto> fulfill(@PathVariable UUID id) { return ResponseEntity.ok(service.fulfill(id)); }
}

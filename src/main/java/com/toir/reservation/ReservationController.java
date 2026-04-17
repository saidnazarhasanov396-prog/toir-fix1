package com.toir.reservation;

import com.toir.reservation.dto.ReservationDto;
import com.toir.reservation.dto.ReservationRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) { this.service = service; }

    @GetMapping
    public List<ReservationDto> list(@RequestParam UUID workOrderId) {
        return service.findByWorkOrder(workOrderId);
    }

    @PostMapping
    public ResponseEntity<ReservationDto> reserve(@Valid @RequestBody ReservationRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reserve(r));
    }

    @PostMapping("/{id}/cancel")
    public ReservationDto cancel(@PathVariable UUID id) { return service.cancel(id); }

    @PostMapping("/{id}/fulfill")
    public ReservationDto fulfill(@PathVariable UUID id) { return service.fulfill(id); }
}

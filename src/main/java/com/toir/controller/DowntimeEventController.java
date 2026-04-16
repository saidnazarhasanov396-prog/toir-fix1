package com.toir.controller;
import com.toir.service.DowntimeEventService;

import com.toir.dto.downtime.DowntimeEventDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/downtimes")
@Tag(name = "downtimes")
public class DowntimeEventController {

    private final DowntimeEventService service;

    public DowntimeEventController(DowntimeEventService service) { this.service = service; }

    @GetMapping
    public List<DowntimeEventDto> list(@RequestParam UUID equipmentId) {
        return service.findByEquipment(equipmentId);
    }

    @PostMapping
    public ResponseEntity<DowntimeEventDto> register(@Valid @RequestBody DowntimeEventDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(r));
    }

    @PostMapping("/{id}/close")
    public DowntimeEventDto close(@PathVariable UUID id, @RequestParam(required = false) Instant endAt) {
        return service.close(id, endAt);
    }
}

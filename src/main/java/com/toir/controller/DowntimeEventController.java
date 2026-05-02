package com.toir.controller;
import com.toir.dto.downtime.DowntimeEventDto;
import com.toir.service.DowntimeEventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/downtimes")
@Tag(name = "downtimes")
public class DowntimeEventController {

    private final DowntimeEventService service;

    public DowntimeEventController(DowntimeEventService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<DowntimeEventDto>> list(@RequestParam UUID equipmentId) {
        return ResponseEntity.ok(service.findByEquipment(equipmentId));
    }

    @PostMapping
    public ResponseEntity<DowntimeEventDto> register(@Valid @RequestBody DowntimeEventDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(r));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<DowntimeEventDto> close(@PathVariable UUID id, @RequestParam(required = false) Instant endAt) {
        return ResponseEntity.ok(service.close(id, endAt));
    }
}

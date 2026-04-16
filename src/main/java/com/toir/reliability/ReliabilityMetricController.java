package com.toir.reliability;

import com.toir.reliability.dto.ReliabilityMetricDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reliability-metrics")
@Tag(name = "reliability-metrics")
public class ReliabilityMetricController {

    private final ReliabilityMetricService service;

    public ReliabilityMetricController(ReliabilityMetricService service) { this.service = service; }

    @GetMapping
    public List<ReliabilityMetricDto> list(@RequestParam UUID equipmentId) {
        return service.findByEquipment(equipmentId);
    }

    @PostMapping
    public ResponseEntity<ReliabilityMetricDto> record(@Valid @RequestBody ReliabilityMetricDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(r));
    }
}

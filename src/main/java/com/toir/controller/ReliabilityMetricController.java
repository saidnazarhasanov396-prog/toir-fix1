package com.toir.controller;
import com.toir.dto.reliability.ReliabilityMetricDto;
import com.toir.service.ReliabilityMetricService;
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
@RequestMapping("/api/v1/reliability-metrics")
@Tag(name = "reliability-metrics")
public class ReliabilityMetricController {

    private final ReliabilityMetricService service;

    public ReliabilityMetricController(ReliabilityMetricService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Page<ReliabilityMetricDto>> list(@RequestParam UUID equipmentId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByEquipment(equipmentId), page, size));
    }

    @PostMapping
    public ResponseEntity<ReliabilityMetricDto> record(@Valid @RequestBody ReliabilityMetricDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(r));
    }
}

package com.toir.oee;

import com.toir.oee.dto.OeeRecordDto;
import com.toir.oee.dto.OeeRecordRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oee")
@Tag(name = "oee")
public class OeeController {

    private final OeeService service;

    public OeeController(OeeService service) {
        this.service = service;
    }

    @GetMapping
    public List<OeeRecordDto> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        if (from != null && to != null) return service.listBetween(equipmentId, from, to);
        if (equipmentId != null) return service.listByEquipment(equipmentId);
        return List.of();
    }

    @GetMapping("/{id}")
    public OeeRecordDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<OeeRecordDto> create(@Valid @RequestBody OeeRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public OeeRecordDto update(@PathVariable UUID id, @Valid @RequestBody OeeRecordRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }

    @GetMapping("/summary")
    public OeeService.OeeSummary summary(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.summary(equipmentId, from, to);
    }
}

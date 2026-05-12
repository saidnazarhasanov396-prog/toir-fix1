package com.toir.controller;
import com.toir.dto.oee.OeeRecordDto;
import com.toir.dto.oee.OeeRecordRequest;
import com.toir.dto.oee.OeeSummary;
import com.toir.service.OeeService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/oee")
@Tag(name = "oee")
@RequiredArgsConstructor
public class OeeController {

    private final OeeService service;

    @GetMapping
    public ResponseEntity<Page<OeeRecordDto>> list(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String equipmentSearch,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (from != null && to != null) return ResponseEntity.ok(PaginationUtils.page(service.listBetween(equipmentId, equipmentSearch, from, to), page, size));
        if (equipmentId != null) return ResponseEntity.ok(PaginationUtils.page(service.listByEquipment(equipmentId), page, size));
        if (equipmentSearch != null && !equipmentSearch.isBlank()) return ResponseEntity.ok(PaginationUtils.page(service.list(equipmentSearch), page, size));
        return ResponseEntity.ok(PaginationUtils.page(List.of(), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OeeRecordDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<OeeRecordDto> create(@Valid @RequestBody OeeRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OeeRecordDto> update(@PathVariable UUID id, @Valid @RequestBody OeeRecordRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public ResponseEntity<OeeSummary> summary(
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String equipmentSearch,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(service.summary(equipmentId, equipmentSearch, from, to));
    }
}

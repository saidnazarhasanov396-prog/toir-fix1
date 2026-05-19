package com.toir.controller;
import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.enums.PlanStatus;
import com.toir.service.PlannedShutdownService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/planned-shutdowns")
@Tag(name = "planned-shutdowns")
@RequiredArgsConstructor
public class PlannedShutdownController {

    private final PlannedShutdownService service;

    @GetMapping
    public ResponseEntity<Page<PlannedShutdownDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) PlanStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAllFiltered(departmentId, status, search), page, size));
    }

    @PostMapping
    public ResponseEntity<PlannedShutdownDto> create(@Valid @RequestBody PlannedShutdownDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PlannedShutdownDto> approve(@PathVariable UUID id) { return ResponseEntity.ok(service.approve(id)); }
}

package com.toir.controller;
import com.toir.dto.plannedshutdown.*;
import com.toir.exception.RestException;
import com.toir.enums.PlanStatus;
import com.toir.service.PlannedShutdownService;
import com.toir.util.PaginationUtils;
import com.toir.util.SortUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/planned-shutdowns")
@Tag(name = "planned-shutdowns")
@RequiredArgsConstructor
public class PlannedShutdownController {

    private final PlannedShutdownService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_READ')")
    public ResponseEntity<Page<PlannedShutdownDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) PlanStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir) {
        List<PlannedShutdownDto> rows = service.findAllFiltered(departmentId, status, search);
        Comparator<PlannedShutdownDto> comparator = switch (sortBy == null ? "" : sortBy.trim()) {
            case "status" -> Comparator.comparing(
                    PlannedShutdownDto::status,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "startAt" -> Comparator.comparing(
                    PlannedShutdownDto::startAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "endAt" -> Comparator.comparing(
                    PlannedShutdownDto::endAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
        if (comparator != null) {
            if (SortUtils.direction(sortDir, Sort.Direction.DESC).isDescending()) {
                comparator = comparator.reversed();
            }
            rows = rows.stream().sorted(comparator).toList();
        }
        return ResponseEntity.ok(PaginationUtils.page(rows, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_CREATE')")
    public ResponseEntity<PlannedShutdownDetailResponse> create(@Valid @RequestBody PlannedShutdownCreateRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_READ')")
    public ResponseEntity<PlannedShutdownDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownDetailResponse> update(
            @PathVariable UUID id, @Valid @RequestBody PlannedShutdownUpdateRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @GetMapping("/{id}/assets")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_READ')")
    public ResponseEntity<PlannedShutdownAssetScopeResponse> getAssets(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getAssets(id));
    }

    @PutMapping("/{id}/assets")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_UPDATE')")
    public ResponseEntity<PlannedShutdownAssetScopeResponse> replaceAssets(
            @PathVariable UUID id, @Valid @RequestBody PlannedShutdownAssetReplaceRequest request) {
        return ResponseEntity.ok(service.replaceAssets(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('PLANNED_SHUTDOWN_APPROVE')")
    public ResponseEntity<PlannedShutdownDto> reject(@PathVariable UUID id) {
        throw RestException.conflict("Use /api/v1/approvals/{id}/reject to reject approval requests");
    }
}

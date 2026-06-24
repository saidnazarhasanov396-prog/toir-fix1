package com.toir.controller;
import com.toir.dto.plannedshutdown.PlannedShutdownDto;
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
    public ResponseEntity<PlannedShutdownDto> create(@Valid @RequestBody PlannedShutdownDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PlannedShutdownDto> reject(@PathVariable UUID id) {
        throw RestException.conflict("Use /api/v1/approvals/{id}/reject to reject approval requests");
    }
}

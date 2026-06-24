package com.toir.controller.maintenance;

import com.toir.dto.maintenancetemplate.MaintenanceOperationDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateRequest;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateStatsResponse;
import com.toir.enums.MaintenanceKind;
import com.toir.service.maintanance.MaintenanceTemplateService;
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
@RequestMapping("/api/v1/maintenance-templates")
@Tag(name = "maintenance-templates")
@RequiredArgsConstructor
public class MaintenanceTemplateController {

    private final MaintenanceTemplateService service;

    @GetMapping("/stats")
    public ResponseEntity<MaintenanceTemplateStatsResponse> getStats(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MaintenanceKind type
    ) {
        return ResponseEntity.ok(service.getStats(search, type));
    }

    @GetMapping public ResponseEntity<Page<MaintenanceTemplateDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MaintenanceKind type
    , @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir) {
        List<MaintenanceTemplateDto> rows = service.findAll(search, type);
        Comparator<MaintenanceTemplateDto> comparator = switch (sortBy == null ? "" : sortBy.trim()) {
            case "maintenanceKind" -> Comparator.comparing(
                    MaintenanceTemplateDto::maintenanceKind,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "normativeLaborHours" -> Comparator.comparingDouble(MaintenanceTemplateDto::normativeLaborHours);
            default -> null;
        };
        if (comparator != null) {
            if (SortUtils.direction(sortDir, Sort.Direction.ASC).isDescending()) {
                comparator = comparator.reversed();
            }
            rows = rows.stream().sorted(comparator).toList();
        }
        return ResponseEntity.ok(PaginationUtils.page(rows, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceTemplateDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<MaintenanceTemplateDto> create(@Valid @RequestBody MaintenanceTemplateRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceTemplateDto> update(@PathVariable UUID id, @Valid @RequestBody MaintenanceTemplateRequest r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/operations")
    public ResponseEntity<MaintenanceOperationDto> addOperation(@PathVariable UUID id, @Valid @RequestBody MaintenanceOperationDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addOperation(id, r));
    }

    @DeleteMapping("/operations/{operationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> removeOperation(@PathVariable UUID operationId) {
        service.removeOperation(operationId);
        return ResponseEntity.noContent().build();
    }
}

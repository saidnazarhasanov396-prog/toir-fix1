package com.toir.controller.maintenance;

import com.toir.dto.maintenanceaction.MaintenanceActionDto;
import com.toir.dto.maintenanceaction.MaintenanceActionRequest;
import com.toir.service.maintanance.MaintenanceActionService;
import com.toir.util.PaginationUtils;
import com.toir.util.SortUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/maintenance-actions")
@Tag(name = "maintenance-actions")
@RequiredArgsConstructor
public class MaintenanceActionController {

    private final MaintenanceActionService service;

    @GetMapping
    public ResponseEntity<Page<MaintenanceActionDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir
    ) {
        List<MaintenanceActionDto> rows = service.findAll(search, active);
        if ("normativeLaborHours".equals(sortBy)) {
            Comparator<MaintenanceActionDto> comparator = Comparator.comparing(
                    MaintenanceActionDto::defaultDurationHours,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if (SortUtils.direction(sortDir, Sort.Direction.ASC).isDescending()) {
                comparator = comparator.reversed();
            }
            rows = rows.stream().sorted(comparator).toList();
        }
        return ResponseEntity.ok(PaginationUtils.page(rows, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceActionDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<MaintenanceActionDto> create(@Valid @RequestBody MaintenanceActionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaintenanceActionDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody MaintenanceActionRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

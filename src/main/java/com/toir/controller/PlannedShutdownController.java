package com.toir.controller;
import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.service.PlannedShutdownService;
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
@RequestMapping("/api/v1/planned-shutdowns")
@Tag(name = "planned-shutdowns")
public class PlannedShutdownController {

    private final PlannedShutdownService service;

    public PlannedShutdownController(PlannedShutdownService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Page<PlannedShutdownDto>> list(@RequestParam UUID departmentId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByDepartment(departmentId), page, size));
    }

    @PostMapping
    public ResponseEntity<PlannedShutdownDto> create(@Valid @RequestBody PlannedShutdownDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PlannedShutdownDto> approve(@PathVariable UUID id) { return ResponseEntity.ok(service.approve(id)); }
}

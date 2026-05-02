package com.toir.controller;
import com.toir.dto.plannedshutdown.PlannedShutdownDto;
import com.toir.service.PlannedShutdownService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
    public ResponseEntity<List<PlannedShutdownDto>> list(@RequestParam UUID departmentId) {
        return ResponseEntity.ok(service.findByDepartment(departmentId));
    }

    @PostMapping
    public ResponseEntity<PlannedShutdownDto> create(@Valid @RequestBody PlannedShutdownDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PlannedShutdownDto> approve(@PathVariable UUID id) { return ResponseEntity.ok(service.approve(id)); }
}

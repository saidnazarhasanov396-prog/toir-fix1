package com.toir.plannedshutdown;

import com.toir.plannedshutdown.dto.PlannedShutdownDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/planned-shutdowns")
@Tag(name = "planned-shutdowns")
public class PlannedShutdownController {

    private final PlannedShutdownService service;

    public PlannedShutdownController(PlannedShutdownService service) { this.service = service; }

    @GetMapping
    public List<PlannedShutdownDto> list(@RequestParam UUID departmentId) {
        return service.findByDepartment(departmentId);
    }

    @PostMapping
    public ResponseEntity<PlannedShutdownDto> create(@Valid @RequestBody PlannedShutdownDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    public PlannedShutdownDto approve(@PathVariable UUID id) { return service.approve(id); }
}

package com.toir.controller;
import com.toir.dto.maintenancekpi.MaintenanceKPIDto;
import com.toir.service.MaintenanceKPIService;
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
@RequestMapping("/api/v1/maintenance-kpis")
@Tag(name = "maintenance-kpis")
public class MaintenanceKPIController {

    private final MaintenanceKPIService service;

    public MaintenanceKPIController(MaintenanceKPIService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Page<MaintenanceKPIDto>> list(@RequestParam UUID departmentId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByDepartment(departmentId), page, size));
    }

    @PostMapping
    public ResponseEntity<MaintenanceKPIDto> record(@Valid @RequestBody MaintenanceKPIDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.record(r));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

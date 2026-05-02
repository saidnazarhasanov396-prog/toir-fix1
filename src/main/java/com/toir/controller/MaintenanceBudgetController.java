package com.toir.controller;
import com.toir.dto.budget.BudgetLineDto;
import com.toir.dto.budget.MaintenanceBudgetDto;
import com.toir.service.MaintenanceBudgetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "budgets")
public class MaintenanceBudgetController {

    private final MaintenanceBudgetService service;

    public MaintenanceBudgetController(MaintenanceBudgetService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<MaintenanceBudgetDto>> list(@RequestParam int year) { return ResponseEntity.ok(service.findByYear(year)); }

    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceBudgetDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<MaintenanceBudgetDto> create(@Valid @RequestBody MaintenanceBudgetDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<MaintenanceBudgetDto> approve(@PathVariable UUID id) { return ResponseEntity.ok(service.approve(id)); }

    @PostMapping("/{id}/lines")
    public ResponseEntity<BudgetLineDto> addLine(@PathVariable UUID id, @Valid @RequestBody BudgetLineDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addLine(id, r));
    }
}

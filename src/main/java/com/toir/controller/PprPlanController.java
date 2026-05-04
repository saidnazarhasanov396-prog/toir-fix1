package com.toir.controller;

import com.toir.dto.pprplanning.PostponeTaskRequest;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.dto.pprplanning.PprPlanRequest;
import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.dto.pprplanning.PprTaskRequest;
import com.toir.service.PprGeneratorService;
import com.toir.service.PprPlanService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ppr-plans")
@Tag(name = "ppr-plans")
@RequiredArgsConstructor
public class PprPlanController {

    private final PprPlanService service;
    private final PprGeneratorService generatorService;

    @GetMapping
    public ResponseEntity<Page<PprPlanDto>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PprPlanDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<PprPlanDto> create(@Valid @RequestBody PprPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PprPlanDto> update(@PathVariable UUID id, @Valid @RequestBody PprPlanRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PprPlanDto> approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        return ResponseEntity.ok(service.approve(id, approverId));
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<PprGeneratorService.GenerationResult> generate(@PathVariable UUID id) {
        return ResponseEntity.ok(generatorService.generateForPlan(id));
    }

    @GetMapping("/{id}/tasks")
    public ResponseEntity<Page<PprTaskDto>> tasks(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findTasksByPlan(id), page, size));
    }

    @PostMapping("/{id}/tasks")
    public ResponseEntity<PprTaskDto> addTask(@PathVariable UUID id, @Valid @RequestBody PprTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addTask(id, request));
    }

    @PostMapping("/tasks/{taskId}/postpone")
    public ResponseEntity<PprTaskDto> postponeTask(@PathVariable UUID taskId, @Valid @RequestBody PostponeTaskRequest request) {
        return ResponseEntity.ok(service.postponeTask(taskId, request));
    }
}

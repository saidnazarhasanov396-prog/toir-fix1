package com.toir.controller;
import com.toir.dto.pprplanning.PostponeTaskRequest;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.dto.pprplanning.PprPlanRequest;
import com.toir.dto.pprplanning.PprTaskDto;
import com.toir.dto.pprplanning.PprTaskRequest;
import com.toir.service.PprGeneratorService;
import com.toir.service.PprPlanService;

import com.toir.dto.pprplanning.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ppr-plans")
@Tag(name = "ppr-plans")
public class PprPlanController {

    private final PprPlanService service;
    private final PprGeneratorService generatorService;

    public PprPlanController(PprPlanService service, PprGeneratorService generatorService) {
        this.service = service;
        this.generatorService = generatorService;
    }

    @GetMapping
    public List<PprPlanDto> list() { return service.findAll(); }

    @GetMapping("/{id}")
    public PprPlanDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<PprPlanDto> create(@Valid @RequestBody PprPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}")
    public PprPlanDto update(@PathVariable UUID id, @Valid @RequestBody PprPlanRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    public PprPlanDto approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        return service.approve(id, approverId);
    }

    @PostMapping("/{id}/generate")
    public PprGeneratorService.GenerationResult generate(@PathVariable UUID id) {
        return generatorService.generateForPlan(id);
    }

    @GetMapping("/{id}/tasks")
    public List<PprTaskDto> tasks(@PathVariable UUID id) { return service.findTasksByPlan(id); }

    @PostMapping("/{id}/tasks")
    public ResponseEntity<PprTaskDto> addTask(@PathVariable UUID id, @Valid @RequestBody PprTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addTask(id, request));
    }

    @PostMapping("/tasks/{taskId}/postpone")
    public PprTaskDto postponeTask(@PathVariable UUID taskId, @Valid @RequestBody PostponeTaskRequest request) {
        return service.postponeTask(taskId, request);
    }
}

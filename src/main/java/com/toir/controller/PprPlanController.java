package com.toir.controller;

import com.toir.dto.pprplanning.PostponeTaskRequest;
import com.toir.dto.pprplanning.PprPlanDto;
import com.toir.dto.pprplanning.PprPlanRequest;
import com.toir.dto.pprplanning.PprPlanStatsResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ppr-plans")
@Tag(name = "ppr-plans")
@RequiredArgsConstructor
public class PprPlanController {

    private static final String PPR_PLAN_READ_AUTH =
            "hasAnyAuthority('read','PPR_PLAN_READ','PPR_PLAN_WRITE','SYSTEM_ADMIN','*')";
    private static final String PPR_PLAN_WRITE_AUTH =
            "hasAnyAuthority('PPR_PLAN_WRITE','SYSTEM_ADMIN','*')";

    private final PprPlanService service;
    private final PprGeneratorService generatorService;

    @GetMapping
    @PreAuthorize(PPR_PLAN_READ_AUTH)
    public ResponseEntity<Page<PprPlanDto>> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.findAll(year, month, departmentId, page, size));
    }

    @GetMapping("/stats")
    @PreAuthorize(PPR_PLAN_READ_AUTH)
    public ResponseEntity<PprPlanStatsResponse> stats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) UUID departmentId
    ) {
        return ResponseEntity.ok(service.getStats(year, month, departmentId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(PPR_PLAN_READ_AUTH)
    public ResponseEntity<PprPlanDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<PprPlanDto> create(@Valid @RequestBody PprPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<PprPlanDto> update(@PathVariable UUID id, @Valid @RequestBody PprPlanRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<PprPlanDto> approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        return ResponseEntity.ok(service.approve(id, approverId));
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<PprGeneratorService.GenerationResult> generate(@PathVariable UUID id) {
        return ResponseEntity.ok(generatorService.generateForPlan(id));
    }

    @GetMapping("/{id}/tasks")
    @PreAuthorize(PPR_PLAN_READ_AUTH)
    public ResponseEntity<Page<PprTaskDto>> tasks(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findTasksByPlan(id), page, size));
    }

    @PostMapping("/{id}/tasks")
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<PprTaskDto> addTask(@PathVariable UUID id, @Valid @RequestBody PprTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addTask(id, request));
    }

    @PostMapping("/tasks/{taskId}/postpone")
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<PprTaskDto> postponeTask(@PathVariable UUID taskId, @Valid @RequestBody PostponeTaskRequest request) {
        return ResponseEntity.ok(service.postponeTask(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/approve")
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<PprTaskDto> approveTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(service.approveTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/start")
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<PprTaskDto> startTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(service.startTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/complete")
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<PprTaskDto> completeTask(
            @PathVariable UUID taskId,
            @RequestParam(required = false) Double actualLaborHours
    ) {
        return ResponseEntity.ok(service.completeTask(taskId, actualLaborHours));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @PreAuthorize(PPR_PLAN_WRITE_AUTH)
    public ResponseEntity<PprTaskDto> cancelTask(
            @PathVariable UUID taskId,
            @RequestParam(required = false) String reason
    ) {
        return ResponseEntity.ok(service.cancelTask(taskId, reason));
    }
}

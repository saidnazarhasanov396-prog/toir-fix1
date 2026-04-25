package com.toir.controller;
import com.toir.service.WorkExecutionService;

import com.toir.dto.workexecution.ExecutionLogDto;
import com.toir.dto.workexecution.WorkExecutionDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "work-executions")
public class WorkExecutionController {

    private final WorkExecutionService service;

    public WorkExecutionController(WorkExecutionService service) { this.service = service; }

    @GetMapping("/execution-logs")
    public Page<ExecutionLogDto> executionLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        return service.findExecutionLogs(page, pageSize);
    }

    @GetMapping("/work-orders/{workOrderId}/executions")
    public List<WorkExecutionDto> list(@PathVariable UUID workOrderId) {
        return service.findByWorkOrder(workOrderId);
    }

    @PostMapping("/work-orders/{workOrderId}/executions")
    public ResponseEntity<WorkExecutionDto> start(@PathVariable UUID workOrderId, @Valid @RequestBody WorkExecutionDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(workOrderId, r));
    }

    @PostMapping("/work-executions/{id}/end")
    public WorkExecutionDto end(@PathVariable UUID id, @RequestBody WorkExecutionDto r) {
        return service.end(id, r);
    }
}

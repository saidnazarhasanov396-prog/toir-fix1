package com.toir.controller;
import com.toir.dto.workexecution.ExecutionLogDto;
import com.toir.dto.workexecution.WorkExecutionDto;
import com.toir.service.WorkExecutionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "work-executions")
public class WorkExecutionController {

    private final WorkExecutionService service;

    public WorkExecutionController(WorkExecutionService service) { this.service = service; }

    @GetMapping("/execution-logs")
    public ResponseEntity<Page<ExecutionLogDto>> executionLogs(
            @RequestParam(required = false) UUID workOrderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        return ResponseEntity.ok(service.findExecutionLogs(workOrderId, page, pageSize));
    }

    @GetMapping("/work-orders/{workOrderId}/executions")
    public ResponseEntity<List<WorkExecutionDto>> list(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(service.findByWorkOrder(workOrderId));
    }

    @PostMapping("/work-orders/{workOrderId}/executions")
    public ResponseEntity<WorkExecutionDto> start(@PathVariable UUID workOrderId, @Valid @RequestBody WorkExecutionDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.start(workOrderId, r));
    }

    @PostMapping("/work-executions/{id}/end")
    public ResponseEntity<WorkExecutionDto> end(@PathVariable UUID id, @RequestBody WorkExecutionDto r) {
        return ResponseEntity.ok(service.end(id, r));
    }
}

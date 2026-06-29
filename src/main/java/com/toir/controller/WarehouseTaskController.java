package com.toir.controller;

import com.toir.dto.warehouse.WarehouseTaskAssignRequest;
import com.toir.dto.warehouse.WarehouseTaskCancelRequest;
import com.toir.dto.warehouse.WarehouseTaskCompleteRequest;
import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.dto.warehouse.WarehouseTaskRequest;
import com.toir.dto.warehouse.WarehouseTaskScanConfirmRequest;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.WarehouseTaskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse/tasks")
@Tag(name = "warehouse-tasks")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WarehouseTaskController {

    private final WarehouseTaskService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_TASK_READ')")
    public ResponseEntity<WarehouseTaskPageResponse> list(
            @RequestParam(required = false) WarehouseTaskStatus status,
            @RequestParam(required = false, name = "type") WarehouseTaskType type,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID assignedToId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(WarehouseTaskPageResponse.from(
                service.findAll(status, type, warehouseId, assignedToId, page, size)
        ));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_TASK_ASSIGN')")
    public ResponseEntity<WarehouseTaskDto> create(@Valid @RequestBody WarehouseTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_TASK_ASSIGN')")
    public ResponseEntity<WarehouseTaskDto> assign(
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseTaskAssignRequest request
    ) {
        return ResponseEntity.ok(service.assign(id, request));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_TASK_EXECUTE')")
    public ResponseEntity<WarehouseTaskDto> start(@PathVariable UUID id) {
        return ResponseEntity.ok(service.start(id));
    }

    @PostMapping("/{id}/lines/{lineId}/scan-confirm")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_TASK_EXECUTE')")
    public ResponseEntity<WarehouseTaskDto> scanConfirm(
            @PathVariable UUID id,
            @PathVariable UUID lineId,
            @RequestBody(required = false) WarehouseTaskScanConfirmRequest request
    ) {
        return ResponseEntity.ok(service.scanConfirm(id, lineId, request));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_TASK_EXECUTE')")
    public ResponseEntity<WarehouseTaskDto> complete(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) WarehouseTaskCompleteRequest request
    ) {
        return ResponseEntity.ok(service.complete(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_TASK_ASSIGN')")
    public ResponseEntity<WarehouseTaskDto> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) WarehouseTaskCancelRequest request
    ) {
        return ResponseEntity.ok(service.cancel(id, request == null ? null : request.reason()));
    }

    public record WarehouseTaskPageResponse(
            List<WarehouseTaskDto> content,
            long totalElements,
            int totalPages,
            int number,
            int size
    ) {
        static WarehouseTaskPageResponse from(Page<WarehouseTaskDto> page) {
            return new WarehouseTaskPageResponse(
                    page.getContent(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.getNumber(),
                    page.getSize()
            );
        }
    }
}

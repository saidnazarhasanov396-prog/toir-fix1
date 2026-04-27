package com.toir.controller;
import com.toir.service.WorkOrderService;
import com.toir.enums.WorkOrderStatus;

import com.toir.security.SecurityScope;
import com.toir.dto.workorder.CloseWorkOrderRequest;
import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders")
@Tag(name = "work-orders")
public class WorkOrderController {

    private final WorkOrderService service;
    private final SecurityScope securityScope;

    public WorkOrderController(WorkOrderService service, SecurityScope securityScope) {
        this.service = service;
        this.securityScope = securityScope;
    }

    @GetMapping
    public Page<WorkOrderDto> list(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search
    ) {
        return service.search(status, securityScope.enforceDepartmentScope(departmentId), equipmentId, page, pageSize, search);
    }

    @GetMapping("/mobile-feed")
    public Page<WorkOrderDto> mobileFeed(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return service.mobileFeed(securityScope.enforceDepartmentScope(departmentId), equipmentId, search, page, pageSize);
    }

    @GetMapping("/{id}")
    public WorkOrderDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<WorkOrderDto> create(@Valid @RequestBody WorkOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/approve")
    public WorkOrderDto approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        return service.approve(id, approverId);
    }

    @PostMapping("/{id}/start")
    public WorkOrderDto start(@PathVariable UUID id) { return service.start(id); }

    @PostMapping("/{id}/complete")
    public WorkOrderDto complete(@PathVariable UUID id, @Valid @RequestBody CompleteWorkOrderRequest request) {
        return service.complete(id, request);
    }

    @PostMapping("/{id}/close")
    public WorkOrderDto close(@PathVariable UUID id, @Valid @RequestBody CloseWorkOrderRequest request) {
        return service.close(id, request);
    }
}

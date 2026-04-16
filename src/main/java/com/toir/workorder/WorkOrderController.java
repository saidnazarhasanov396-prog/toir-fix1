package com.toir.workorder;

import com.toir.common.security.SecurityScope;
import com.toir.workorder.dto.CloseWorkOrderRequest;
import com.toir.workorder.dto.WorkOrderDto;
import com.toir.workorder.dto.WorkOrderRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/work-orders")
@Tag(name = "work-orders")
public class WorkOrderController {

    private final WorkOrderService service;
    private final SecurityScope securityScope;

    public WorkOrderController(WorkOrderService service, SecurityScope securityScope) {
        this.service = service;
        this.securityScope = securityScope;
    }

    @GetMapping
    public List<WorkOrderDto> list(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId
    ) {
        return service.search(status, securityScope.enforceDepartmentScope(departmentId), equipmentId);
    }

    @GetMapping("/mobile-feed")
    public List<WorkOrderDto> mobileFeed(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId
    ) {
        UUID scope = securityScope.enforceDepartmentScope(departmentId);
        List<WorkOrderDto> approved = service.search(WorkOrderStatus.APPROVED, scope, equipmentId);
        List<WorkOrderDto> inProgress = service.search(WorkOrderStatus.IN_PROGRESS, scope, equipmentId);
        return java.util.stream.Stream.concat(approved.stream(), inProgress.stream()).toList();
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

    @PostMapping("/{id}/close")
    public WorkOrderDto close(@PathVariable UUID id, @Valid @RequestBody CloseWorkOrderRequest request) {
        return service.close(id, request);
    }
}

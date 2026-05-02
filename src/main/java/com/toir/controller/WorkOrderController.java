package com.toir.controller;
import com.toir.dto.workorder.CloseWorkOrderRequest;
import com.toir.dto.workorder.CompleteWorkOrderRequest;
import com.toir.dto.workorder.WorkOrderDto;
import com.toir.dto.workorder.WorkOrderRequest;
import com.toir.enums.WorkOrderStatus;
import com.toir.security.SecurityScope;
import com.toir.service.WorkOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Page<WorkOrderDto>> list(
            @RequestParam(required = false) WorkOrderStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.search(status, securityScope.enforceDepartmentScope(departmentId), equipmentId, page, size, search));
    }

    @GetMapping("/mobile-feed")
    public ResponseEntity<Page<WorkOrderDto>> mobileFeed(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(service.mobileFeed(securityScope.enforceDepartmentScope(departmentId), equipmentId, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkOrderDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<WorkOrderDto> create(@Valid @RequestBody WorkOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<WorkOrderDto> approve(@PathVariable UUID id, @RequestParam UUID approverId) {
        return ResponseEntity.ok(service.approve(id, approverId));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<WorkOrderDto> start(@PathVariable UUID id) { return ResponseEntity.ok(service.start(id)); }

    @PostMapping("/{id}/complete")
    public ResponseEntity<WorkOrderDto> complete(@PathVariable UUID id, @Valid @RequestBody CompleteWorkOrderRequest request) {
        return ResponseEntity.ok(service.complete(id, request));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<WorkOrderDto> close(@PathVariable UUID id, @Valid @RequestBody CloseWorkOrderRequest request) {
        return ResponseEntity.ok(service.close(id, request));
    }
}

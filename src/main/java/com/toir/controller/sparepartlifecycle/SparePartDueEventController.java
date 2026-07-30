package com.toir.controller.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.AcknowledgeSparePartDueEventRequest;
import com.toir.dto.sparepartlifecycle.CreateDueEventWorkOrderRequest;
import com.toir.dto.sparepartlifecycle.DueEventWorkOrderActionResponse;
import com.toir.entity.sparepartlifecycle.SparePartDueEvent;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.security.ScopeAccessService;
import com.toir.service.sparepartlifecycle.SparePartDueEventService;
import com.toir.service.sparepartlifecycle.SparePartDueWorkOrderService;
import com.toir.util.PaginationUtils;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spare-part-due-events")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class SparePartDueEventController {

    private final SparePartDueEventService service;
    private final ScopeAccessService scopeAccessService;
    private final SparePartDueWorkOrderService dueWorkOrderService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_DUE_READ')")
    public ResponseEntity<Page<SparePartDueEvent>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PaginationUtils.page(service.list(), page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_DUE_READ')")
    public ResponseEntity<SparePartDueEvent> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping("/equipment/{equipmentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_DUE_READ')")
    public ResponseEntity<java.util.List<SparePartDueEvent>> listForEquipment(
            @PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.listForEquipment(equipmentId));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('*') or hasAuthority('SPARE_PART_DUE_ACKNOWLEDGE')")
    public ResponseEntity<SparePartDueEvent> acknowledge(
            @PathVariable UUID id,
            @RequestBody(required = false) AcknowledgeSparePartDueEventRequest request
    ) {
        return ResponseEntity.ok(service.acknowledge(
                id,
                scopeAccessService.currentUserIdOrNull(),
                request == null ? null : request.acknowledgedAt()
        ));
    }

    @PostMapping("/{id}/work-orders")
    @PreAuthorize("hasAuthority('*') or hasAuthority('SPARE_PART_DUE_WORK_ORDER_CREATE')")
    public ResponseEntity<DueEventWorkOrderActionResponse> createWorkOrder(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) CreateDueEventWorkOrderRequest request
    ) {
        return ResponseEntity.ok(dueWorkOrderService.create(
                id, idempotencyKey, scopeAccessService.currentUserIdOrNull(), request));
    }
}

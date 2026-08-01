package com.toir.controller;

import com.toir.dto.workorder.WorkOrderCompletionEvidenceDto;
import com.toir.entity.maintenance.WorkOrder;
import com.toir.exception.RestException;
import com.toir.repository.WorkOrderCompletionEvidenceRepository;
import com.toir.repository.WorkOrderRepository;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.security.ScopeAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/completion-evidence")
@Tag(name = "work-order-completion-evidence")
@RequiredArgsConstructor
@RequiresSensitiveAccess
public class WorkOrderCompletionEvidenceController {

    private final WorkOrderCompletionEvidenceRepository evidence;
    private final WorkOrderRepository workOrders;
    private final ScopeAccessService scopeAccessService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<List<WorkOrderCompletionEvidenceDto>> list(@PathVariable UUID workOrderId) {
        WorkOrder workOrder = workOrders.findByIdAndIsDeletedFalse(workOrderId)
                .orElseThrow(() -> RestException.notFound("Work order not found: " + workOrderId));
        scopeAccessService.assertCanAccessDepartment(workOrder.getDepartmentId());
        List<WorkOrderCompletionEvidenceDto> result = evidence
                .findAllByWorkOrderIdAndIsDeletedFalse(workOrderId)
                .stream()
                .map(WorkOrderCompletionEvidenceDto::from)
                .sorted(Comparator.comparing(
                        WorkOrderCompletionEvidenceDto::capturedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return ResponseEntity.ok(result);
    }
}

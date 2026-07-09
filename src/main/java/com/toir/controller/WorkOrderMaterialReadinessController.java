package com.toir.controller;

import com.toir.dto.workorder.WorkOrderMaterialReadinessDto;
import com.toir.service.WorkOrderMaterialReadinessService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/material-readiness")
@RequiredArgsConstructor
public class WorkOrderMaterialReadinessController {

    private final WorkOrderMaterialReadinessService readinessService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public WorkOrderMaterialReadinessDto getMaterialReadiness(@PathVariable UUID workOrderId) {
        return readinessService.getReadiness(workOrderId);
    }
}

package com.toir.controller;

import com.toir.dto.workorder.WorkOrderCloseReadinessDto;
import com.toir.service.WorkOrderService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/close-readiness")
@RequiredArgsConstructor
public class WorkOrderCloseReadinessController {

    private final WorkOrderService workOrderService;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public WorkOrderCloseReadinessDto getCloseReadiness(@PathVariable UUID workOrderId) {
        return workOrderService.getCloseReadiness(workOrderId);
    }
}

package com.toir.controller;

import com.toir.dto.workorder.WorkOrderSparePartRequirementDto;
import com.toir.service.maintanance.WorkOrderSparePartRequirementService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/work-orders")
@Tag(name = "work-order-spare-part-requirements")
@RequiredArgsConstructor
public class WorkOrderSparePartRequirementController {

    private final WorkOrderSparePartRequirementService service;

    @GetMapping("/{workOrderId}/spare-part-requirements")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ')")
    public ResponseEntity<List<WorkOrderSparePartRequirementDto>> list(@PathVariable UUID workOrderId) {
        return ResponseEntity.ok(service.findByWorkOrder(workOrderId));
    }
}

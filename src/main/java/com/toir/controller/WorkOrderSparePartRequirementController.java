package com.toir.controller;

import com.toir.dto.workorder.WorkOrderSparePartRequirementDto;
import com.toir.dto.workorder.WorkOrderSparePartRequirementRequest;
import com.toir.service.maintanance.WorkOrderSparePartRequirementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PostMapping("/{workOrderId}/spare-part-requirements")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_UPDATE')")
    public ResponseEntity<WorkOrderSparePartRequirementDto> create(
            @PathVariable UUID workOrderId,
            @Valid @RequestBody WorkOrderSparePartRequirementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createManual(workOrderId, request));
    }

    @PutMapping("/{workOrderId}/spare-part-requirements/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_UPDATE')")
    public ResponseEntity<WorkOrderSparePartRequirementDto> update(
            @PathVariable UUID workOrderId,
            @PathVariable UUID id,
            @Valid @RequestBody WorkOrderSparePartRequirementRequest request) {
        return ResponseEntity.ok(service.updateManual(workOrderId, id, request));
    }

    @DeleteMapping("/{workOrderId}/spare-part-requirements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_UPDATE')")
    public ResponseEntity<Void> delete(@PathVariable UUID workOrderId, @PathVariable UUID id) {
        service.deleteManual(workOrderId, id);
        return ResponseEntity.noContent().build();
    }
}

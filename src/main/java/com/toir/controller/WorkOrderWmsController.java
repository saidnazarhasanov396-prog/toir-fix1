package com.toir.controller;

import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.dto.workorder.WorkOrderMaterialReturnDto;
import com.toir.dto.workorder.WorkOrderMaterialReturnRequest;
import com.toir.dto.workorder.WorkOrderPickConfirmRequest;
import com.toir.dto.workorder.WorkOrderPickListRequest;
import com.toir.dto.workorder.WorkOrderWmsReservationRequest;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.WorkOrderWmsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}")
@Tag(name = "work-order-wms")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WorkOrderWmsController {

    private final WorkOrderWmsService service;

    @PostMapping("/wms-reservations")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_PICK') or hasAuthority('STOCK_MOVE')")
    public ResponseEntity<List<ReservationDto>> reserve(
            @PathVariable UUID workOrderId,
            @Valid @RequestBody WorkOrderWmsReservationRequest request
    ) {
        return ResponseEntity.ok(service.reserve(workOrderId, request));
    }

    @PostMapping("/pick-list")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_PICK') or hasAuthority('STOCK_MOVE')")
    public ResponseEntity<WarehouseTaskDto> createPickList(
            @PathVariable UUID workOrderId,
            @RequestBody(required = false) WorkOrderPickListRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPickList(workOrderId, request));
    }

    @PostMapping("/pick-list/{pickListId}/confirm")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_PICK') or hasAuthority('MATERIAL_USAGE_ISSUE')")
    public ResponseEntity<List<RepairMaterialUsageDto>> confirmPick(
            @PathVariable UUID workOrderId,
            @PathVariable UUID pickListId,
            @Valid @RequestBody WorkOrderPickConfirmRequest request
    ) {
        return ResponseEntity.ok(service.confirmPick(workOrderId, pickListId, request));
    }

    @PostMapping("/material-returns")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_PICK') or hasAuthority('INVENTORY_RETURN')")
    public ResponseEntity<WorkOrderMaterialReturnDto> returnMaterial(
            @PathVariable UUID workOrderId,
            @RequestBody(required = false) WorkOrderMaterialReturnRequest request
    ) {
        return ResponseEntity.ok(service.returnMaterial(workOrderId, request));
    }
}

package com.toir.controller.repair;
import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.service.repair.RepairMaterialUsageService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "repair-material-usage")
@RequiredArgsConstructor
public class RepairMaterialUsageController {

    private final RepairMaterialUsageService service;

    @GetMapping("/work-orders/{workOrderId}/material-usage")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MATERIAL_USAGE_READ')")
    public ResponseEntity<Page<RepairMaterialUsageDto>> list(@PathVariable UUID workOrderId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByWorkOrder(workOrderId), page, size));
    }

    @GetMapping("/repair-requests/{repairRequestId}/material-usage")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MATERIAL_USAGE_READ')")
    public ResponseEntity<Page<RepairMaterialUsageDto>> listByRepairRequest(
            @PathVariable UUID repairRequestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByRepairRequest(repairRequestId), page, size));
    }

    @GetMapping("/ppr-plans/tasks/material-counts")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MATERIAL_USAGE_READ')")
    public ResponseEntity<Map<UUID, Long>> countByPprTasks(
            @RequestParam List<UUID> taskIds) {
        return ResponseEntity.ok(service.countByPprTaskIds(taskIds));
    }

    @GetMapping("/ppr-plans/tasks/{taskId}/material-usage")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MATERIAL_USAGE_READ')")
    public ResponseEntity<Page<RepairMaterialUsageDto>> listByPprTask(
            @PathVariable UUID taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByPprTask(taskId), page, size));
    }

    @PostMapping("/work-orders/{workOrderId}/material-usage")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MATERIAL_USAGE_ISSUE')")
    public ResponseEntity<RepairMaterialUsageDto> register(@PathVariable UUID workOrderId, @Valid @RequestBody RepairMaterialUsageDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(workOrderId, r));
    }
}

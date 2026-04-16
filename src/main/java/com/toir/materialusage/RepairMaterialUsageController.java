package com.toir.materialusage;

import com.toir.materialusage.dto.RepairMaterialUsageDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "repair-material-usage")
public class RepairMaterialUsageController {

    private final RepairMaterialUsageService service;

    public RepairMaterialUsageController(RepairMaterialUsageService service) { this.service = service; }

    @GetMapping("/work-orders/{workOrderId}/material-usage")
    public List<RepairMaterialUsageDto> list(@PathVariable UUID workOrderId) {
        return service.findByWorkOrder(workOrderId);
    }

    @PostMapping("/work-orders/{workOrderId}/material-usage")
    public ResponseEntity<RepairMaterialUsageDto> register(@PathVariable UUID workOrderId, @Valid @RequestBody RepairMaterialUsageDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(workOrderId, r));
    }
}

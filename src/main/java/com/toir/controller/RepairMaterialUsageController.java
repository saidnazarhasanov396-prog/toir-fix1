package com.toir.controller;
import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.service.RepairMaterialUsageService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "repair-material-usage")
public class RepairMaterialUsageController {

    private final RepairMaterialUsageService service;

    public RepairMaterialUsageController(RepairMaterialUsageService service) { this.service = service; }

    @GetMapping("/work-orders/{workOrderId}/material-usage")
    public ResponseEntity<Page<RepairMaterialUsageDto>> list(@PathVariable UUID workOrderId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByWorkOrder(workOrderId), page, size));
    }

    @PostMapping("/work-orders/{workOrderId}/material-usage")
    public ResponseEntity<RepairMaterialUsageDto> register(@PathVariable UUID workOrderId, @Valid @RequestBody RepairMaterialUsageDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(workOrderId, r));
    }
}

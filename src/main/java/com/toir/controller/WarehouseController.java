package com.toir.controller;
import com.toir.dto.warehouse.WarehouseDto;
import com.toir.dto.warehouse.WarehouseEquipmentAssignRequest;
import com.toir.dto.warehouse.WarehouseEquipmentItemDto;
import com.toir.dto.warehouse.WarehouseEquipmentStatusUpdateRequest;
import com.toir.dto.warehouse.WarehouseRequest;
import com.toir.dto.warehouse.WarehouseStockDto;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.service.WarehouseService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/warehouses")
@Tag(name = "warehouses")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService service;
    private final WarehouseEquipmentItemService warehouseEquipmentItemService;

    @GetMapping
    public ResponseEntity<Page<WarehouseDto>> list(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) UUID departmentId,
                                   @RequestParam(name = "department_id", required = false) UUID departmentIdAlias,
                                   @RequestParam(required = false) UUID locationId,
                                   @RequestParam(name = "location_id", required = false) UUID locationIdAlias,
                                   @RequestParam(required = false) UUID responsibleId,
                                   @RequestParam(name = "responsible_id", required = false) UUID responsibleIdAlias,
                                   @RequestParam(required = false) Boolean active,
                                   @RequestParam(name = "is_active", required = false) Boolean activeAlias, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(
                search,
                firstNonNull(departmentId, departmentIdAlias),
                firstNonNull(locationId, locationIdAlias),
                firstNonNull(responsibleId, responsibleIdAlias),
                firstNonNull(active, activeAlias)
        ), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @GetMapping("/{id}/stocks")
    public ResponseEntity<Page<WarehouseStockDto>> stocks(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return ResponseEntity.ok(PaginationUtils.page(service.findStocks(id), page, size)); }

    @PostMapping("/{warehouseId}/equipment")
    public ResponseEntity<WarehouseEquipmentItemDto> assignEquipment(@PathVariable UUID warehouseId,
                                                                      @Valid @RequestBody WarehouseEquipmentAssignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warehouseEquipmentItemService.assign(warehouseId, request));
    }

    @GetMapping("/{warehouseId}/equipment")
    public ResponseEntity<Page<WarehouseEquipmentItemDto>> listEquipment(@PathVariable UUID warehouseId,
                                                                          @RequestParam(required = false) WarehouseEquipmentStatus status,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(warehouseEquipmentItemService.list(warehouseId, status, Math.max(0, page), Math.max(1, size)));
    }

    @PatchMapping("/{warehouseId}/equipment/{equipmentId}/status")
    public ResponseEntity<WarehouseEquipmentItemDto> updateEquipmentStatus(@PathVariable UUID warehouseId,
                                                                            @PathVariable UUID equipmentId,
                                                                            @Valid @RequestBody WarehouseEquipmentStatusUpdateRequest request) {
        return ResponseEntity.ok(warehouseEquipmentItemService.updateStatus(warehouseId, equipmentId, request.status()));
    }

    @DeleteMapping("/{warehouseId}/equipment/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> unassignEquipment(@PathVariable UUID warehouseId,
                                                  @PathVariable UUID equipmentId) {
        warehouseEquipmentItemService.remove(warehouseId, equipmentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<WarehouseDto> create(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WarehouseDto> update(@PathVariable UUID id, @Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private <T> T firstNonNull(T primary, T alias) {
        return primary != null ? primary : alias;
    }
}

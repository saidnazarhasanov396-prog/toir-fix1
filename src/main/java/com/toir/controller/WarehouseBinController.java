package com.toir.controller;

import com.toir.dto.warehouse.WarehouseBinDto;
import com.toir.dto.warehouse.WarehouseBinRequest;
import com.toir.dto.warehouse.WarehouseBinStatusRequest;
import com.toir.dto.warehouse.WarehouseStockBalanceDto;
import com.toir.enums.WarehouseBinType;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.warehouse.WarehouseBinService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
@Tag(name = "warehouse-bins")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class WarehouseBinController {

    private final WarehouseBinService service;

    @GetMapping("/{warehouseId}/bins")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_READ') or hasAuthority('WAREHOUSE_READ') or hasAuthority('STOCK_READ')")
    public ResponseEntity<Page<WarehouseBinDto>> list(@PathVariable UUID warehouseId,
                                                      @RequestParam(required = false) String search,
                                                      @RequestParam(required = false) String zone,
                                                      @RequestParam(required = false) String aisle,
                                                      @RequestParam(required = false) String rack,
                                                      @RequestParam(required = false) String shelfLevel,
                                                      @RequestParam(required = false) WarehouseBinType binType,
                                                      @RequestParam(required = false) WarehouseQualityZoneType qualityZoneType,
                                                      @RequestParam(required = false) String temperatureZone,
                                                      @RequestParam(required = false) String hazardClass,
                                                      @RequestParam(required = false) Boolean active,
                                                      @RequestParam(required = false) Boolean blocked,
                                                      @RequestParam(required = false) Boolean frozen,
                                                      @RequestParam(required = false) Integer binLevel,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(
                warehouseId,
                search,
                zone,
                aisle,
                rack,
                shelfLevel,
                binType,
                qualityZoneType,
                temperatureZone,
                hazardClass,
                active,
                blocked,
                frozen,
                binLevel,
                page,
                size
        ));
    }

    @GetMapping("/bins")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_READ') or hasAuthority('WAREHOUSE_READ') or hasAuthority('STOCK_READ')")
    public ResponseEntity<Page<WarehouseBinDto>> listAll(@RequestParam(required = false) UUID warehouseId,
                                                         @RequestParam(required = false) String search,
                                                         @RequestParam(required = false) String zone,
                                                         @RequestParam(required = false) String aisle,
                                                         @RequestParam(required = false) String rack,
                                                         @RequestParam(required = false) String shelfLevel,
                                                         @RequestParam(required = false) WarehouseBinType binType,
                                                         @RequestParam(required = false) WarehouseQualityZoneType qualityZoneType,
                                                         @RequestParam(required = false) String temperatureZone,
                                                         @RequestParam(required = false) String hazardClass,
                                                         @RequestParam(required = false) Boolean active,
                                                         @RequestParam(required = false) Boolean blocked,
                                                         @RequestParam(required = false) Boolean frozen,
                                                         @RequestParam(required = false) Integer binLevel,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(
                warehouseId,
                search,
                zone,
                aisle,
                rack,
                shelfLevel,
                binType,
                qualityZoneType,
                temperatureZone,
                hazardClass,
                active,
                blocked,
                frozen,
                binLevel,
                page,
                size
        ));
    }

    @GetMapping("/{warehouseId}/bins/{binId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_READ') or hasAuthority('WAREHOUSE_READ') or hasAuthority('STOCK_READ')")
    public ResponseEntity<WarehouseBinDto> get(@PathVariable UUID warehouseId, @PathVariable UUID binId) {
        return ResponseEntity.ok(service.get(warehouseId, binId));
    }

    @PostMapping("/{warehouseId}/bins")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_MANAGE')")
    public ResponseEntity<WarehouseBinDto> create(
            @PathVariable UUID warehouseId,
            @Valid @RequestBody WarehouseBinRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(warehouseId, request));
    }

    @PutMapping("/{warehouseId}/bins/{binId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_MANAGE')")
    public ResponseEntity<WarehouseBinDto> update(
            @PathVariable UUID warehouseId,
            @PathVariable UUID binId,
            @Valid @RequestBody WarehouseBinRequest request
    ) {
        return ResponseEntity.ok(service.update(warehouseId, binId, request));
    }

    @PostMapping("/{warehouseId}/bins/{binId}/block")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_MANAGE')")
    public ResponseEntity<WarehouseBinDto> block(
            @PathVariable UUID warehouseId,
            @PathVariable UUID binId,
            @RequestBody(required = false) WarehouseBinStatusRequest request
    ) {
        return ResponseEntity.ok(service.block(warehouseId, binId, request));
    }

    @PostMapping("/{warehouseId}/bins/{binId}/unblock")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_MANAGE')")
    public ResponseEntity<WarehouseBinDto> unblock(@PathVariable UUID warehouseId, @PathVariable UUID binId) {
        return ResponseEntity.ok(service.unblock(warehouseId, binId));
    }

    @PostMapping("/{warehouseId}/bins/{binId}/freeze")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_MANAGE')")
    public ResponseEntity<WarehouseBinDto> freeze(
            @PathVariable UUID warehouseId,
            @PathVariable UUID binId,
            @RequestBody(required = false) WarehouseBinStatusRequest request
    ) {
        return ResponseEntity.ok(service.freeze(warehouseId, binId, request));
    }

    @PostMapping("/{warehouseId}/bins/{binId}/unfreeze")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_MANAGE')")
    public ResponseEntity<WarehouseBinDto> unfreeze(@PathVariable UUID warehouseId, @PathVariable UUID binId) {
        return ResponseEntity.ok(service.unfreeze(warehouseId, binId));
    }

    @GetMapping("/{warehouseId}/bins/{binId}/stock-balances")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WAREHOUSE_BIN_READ') or hasAuthority('WAREHOUSE_READ') or hasAuthority('STOCK_READ')")
    public ResponseEntity<List<WarehouseStockBalanceDto>> stockBalances(
            @PathVariable UUID warehouseId,
            @PathVariable UUID binId
    ) {
        return ResponseEntity.ok(service.stockBalances(warehouseId, binId));
    }
}

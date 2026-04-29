package com.toir.controller;
import com.toir.service.WarehouseService;

import com.toir.dto.warehouse.WarehouseDto;
import com.toir.dto.warehouse.WarehouseRequest;
import com.toir.dto.warehouse.WarehouseStockDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
@Tag(name = "warehouses")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @GetMapping
    public List<WarehouseDto> list(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) UUID departmentId,
                                   @RequestParam(name = "department_id", required = false) UUID departmentIdAlias,
                                   @RequestParam(required = false) UUID locationId,
                                   @RequestParam(name = "location_id", required = false) UUID locationIdAlias,
                                   @RequestParam(required = false) UUID responsibleId,
                                   @RequestParam(name = "responsible_id", required = false) UUID responsibleIdAlias,
                                   @RequestParam(required = false) Boolean active,
                                   @RequestParam(name = "is_active", required = false) Boolean activeAlias) {
        return service.findAll(
                search,
                firstNonNull(departmentId, departmentIdAlias),
                firstNonNull(locationId, locationIdAlias),
                firstNonNull(responsibleId, responsibleIdAlias),
                firstNonNull(active, activeAlias)
        );
    }

    @GetMapping("/{id}")
    public WarehouseDto get(@PathVariable UUID id) { return service.findById(id); }

    @GetMapping("/{id}/stocks")
    public List<WarehouseStockDto> stocks(@PathVariable UUID id) { return service.findStocks(id); }

    @PostMapping
    public ResponseEntity<WarehouseDto> create(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public WarehouseDto update(@PathVariable UUID id, @Valid @RequestBody WarehouseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }

    private <T> T firstNonNull(T primary, T alias) {
        return primary != null ? primary : alias;
    }
}

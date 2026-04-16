package com.toir.warehouse;

import com.toir.warehouse.dto.WarehouseDto;
import com.toir.warehouse.dto.WarehouseRequest;
import com.toir.warehouse.dto.WarehouseStockDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/warehouses")
@Tag(name = "warehouses")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @GetMapping
    public List<WarehouseDto> list() { return service.findAll(); }

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
}

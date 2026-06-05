package com.toir.controller;
import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.dto.stockmovement.StockMovementRequest;
import com.toir.service.StockMovementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stock-movements")
@Tag(name = "stock-movements")
@RequiredArgsConstructor
public class StockMovementController {

    private final StockMovementService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('STOCK_READ')")
    public ResponseEntity<Page<StockMovementDto>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.findAll(page, size));
    }

    @PostMapping
    @PreAuthorize("""
            hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or
            (#request.type() == T(com.toir.enums.StockMovementType).RECEIPT and hasAuthority('STOCK_RECEIVE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).RETURN and hasAuthority('STOCK_RECEIVE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).ISSUE and hasAuthority('STOCK_ISSUE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).TRANSFER and hasAuthority('STOCK_MOVE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).RESERVATION and hasAuthority('STOCK_MOVE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).RELEASE and hasAuthority('STOCK_MOVE')) or
            (#request.type() == T(com.toir.enums.StockMovementType).ADJUSTMENT and hasAuthority('STOCK_ADJUST'))
            """)
    public ResponseEntity<StockMovementDto> create(@Valid @RequestBody StockMovementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }
}

package com.toir.controller;

import com.toir.dto.supplier.SupplierDto;
import com.toir.dto.supplier.SupplierPerformanceDto;
import com.toir.dto.supplier.SupplierRequest;
import com.toir.enums.SupplierType;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.SupplierService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
@Tag(name = "suppliers")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.SUPPLIER_READ + "')")
    public ResponseEntity<Page<SupplierDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) SupplierType supplierType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(search, active, supplierType), page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.SUPPLIER_READ + "')")
    public ResponseEntity<SupplierDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.SUPPLIER_CREATE + "')")
    public ResponseEntity<SupplierDto> create(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.SUPPLIER_UPDATE + "')")
    public ResponseEntity<SupplierDto> update(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.SUPPLIER_UPDATE + "')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/performance")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.SUPPLIER_READ + "')")
    public ResponseEntity<SupplierPerformanceDto> performance(@PathVariable UUID id) {
        return ResponseEntity.ok(service.performance(id));
    }
}

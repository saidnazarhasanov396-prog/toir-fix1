package com.toir.controller.contractor;
import com.toir.dto.contractorwork.ContractorWorkDto;
import com.toir.service.contactor.ContractorWorkService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/contractor-works")
@Tag(name = "contractor-works")
@RequiredArgsConstructor
public class ContractorWorkController {

    private final ContractorWorkService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('CONTRACTOR_READ')")
    public ResponseEntity<Page<ContractorWorkDto>> list(
            @Parameter(description = "Optional contractor filter. When omitted, all contractor works are returned.")
            @RequestParam(required = false) UUID contractorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByContractor(contractorId), page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('CONTRACTOR_CREATE')")
    public ResponseEntity<ContractorWorkDto> create(@Valid @RequestBody ContractorWorkDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('CONTRACTOR_UPDATE')")
    public ResponseEntity<ContractorWorkDto> start(@PathVariable UUID id) { return ResponseEntity.ok(service.start(id)); }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('CONTRACTOR_UPDATE')")
    public ResponseEntity<ContractorWorkDto> complete(@PathVariable UUID id, @RequestParam String result, @RequestParam(required = false) Double cost) {
        return ResponseEntity.ok(service.complete(id, result, cost));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('CONTRACTOR_UPDATE')")
    public ResponseEntity<ContractorWorkDto> accept(@PathVariable UUID id, @RequestParam UUID acceptedById, @RequestParam(required = false) String comment) {
        return ResponseEntity.ok(service.accept(id, acceptedById, comment));
    }
}

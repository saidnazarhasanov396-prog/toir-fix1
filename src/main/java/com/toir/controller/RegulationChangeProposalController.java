package com.toir.controller;

import com.toir.dto.regulationchangeproposal.RegulationChangeProposalDto;
import com.toir.dto.regulationchangeproposal.RegulationChangeProposalRequest;
import com.toir.dto.regulationchangeproposal.RegulationChangeProposalReviewRequest;
import com.toir.service.RegulationChangeProposalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/regulation-change-proposals")
@Tag(name = "regulation-change-proposals")
@RequiredArgsConstructor
public class RegulationChangeProposalController {

    private static final String READ_AUTH =
            "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_READ')";
    private static final String MUTATE_AUTH =
            "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_MANAGE')";
    private static final String APPROVE_AUTH =
            "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('MAINTENANCE_APPROVE')";

    private final RegulationChangeProposalService service;

    @GetMapping
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<List<RegulationChangeProposalDto>> list(
            @RequestParam(required = false) UUID regulationId
    ) {
        return ResponseEntity.ok(service.list(regulationId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<RegulationChangeProposalDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<RegulationChangeProposalDto> create(
            @Valid @RequestBody RegulationChangeProposalRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(MUTATE_AUTH)
    public ResponseEntity<RegulationChangeProposalDto> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE_AUTH)
    public ResponseEntity<RegulationChangeProposalDto> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) RegulationChangeProposalReviewRequest request
    ) {
        return ResponseEntity.ok(service.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(APPROVE_AUTH)
    public ResponseEntity<RegulationChangeProposalDto> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) RegulationChangeProposalReviewRequest request
    ) {
        return ResponseEntity.ok(service.reject(id, request));
    }
}
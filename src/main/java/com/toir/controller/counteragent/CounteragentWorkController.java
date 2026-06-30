package com.toir.controller.counteragent;

import com.toir.dto.counteragent.CounteragentWorkDto;
import com.toir.security.PermissionConstants;
import com.toir.service.counteragent.CounteragentWorkService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/counteragent-works")
@Tag(name = "counteragent-works")
@RequiredArgsConstructor
public class CounteragentWorkController {

    private final CounteragentWorkService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_READ + "')")
    public ResponseEntity<Page<CounteragentWorkDto>> list(
            @Parameter(description = "Optional counteragent filter. When omitted, all counteragent works are returned.")
            @RequestParam(required = false) UUID counteragentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByCounteragent(counteragentId), page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_CREATE + "')")
    public ResponseEntity<CounteragentWorkDto> create(@Valid @RequestBody CounteragentWorkDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_UPDATE + "')")
    public ResponseEntity<CounteragentWorkDto> start(@PathVariable UUID id) {
        return ResponseEntity.ok(service.start(id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_UPDATE + "')")
    public ResponseEntity<CounteragentWorkDto> complete(
            @PathVariable UUID id,
            @RequestParam String result,
            @RequestParam(required = false) Double cost
    ) {
        return ResponseEntity.ok(service.complete(id, result, cost));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_UPDATE + "')")
    public ResponseEntity<CounteragentWorkDto> accept(
            @PathVariable UUID id,
            @RequestParam UUID acceptedById,
            @RequestParam(required = false) String comment
    ) {
        return ResponseEntity.ok(service.accept(id, acceptedById, comment));
    }
}

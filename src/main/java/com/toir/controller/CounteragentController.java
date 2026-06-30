package com.toir.controller;

import com.toir.dto.counteragent.CounteragentDto;
import com.toir.dto.counteragent.CounteragentPerformanceDto;
import com.toir.dto.counteragent.CounteragentRequest;
import com.toir.enums.CounteragentStatus;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.CounteragentService;
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
@RequestMapping("/api/v1/counteragents")
@Tag(name = "counteragents")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class CounteragentController {

    private final CounteragentService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_READ + "')")
    public ResponseEntity<Page<CounteragentDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CounteragentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(search, status), page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_READ + "')")
    public ResponseEntity<CounteragentDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_CREATE + "')")
    public ResponseEntity<CounteragentDto> create(@Valid @RequestBody CounteragentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_UPDATE + "')")
    public ResponseEntity<CounteragentDto> update(@PathVariable UUID id, @Valid @RequestBody CounteragentRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_DELETE + "')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/performance")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_READ + "')")
    public ResponseEntity<CounteragentPerformanceDto> performance(@PathVariable UUID id) {
        return ResponseEntity.ok(service.performance(id));
    }
}

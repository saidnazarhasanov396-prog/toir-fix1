package com.toir.controller.counteragent;

import com.toir.dto.counteragent.CounteragentContractDto;
import com.toir.security.PermissionConstants;
import com.toir.service.counteragent.CounteragentContractService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/counteragent-contracts")
@Tag(name = "counteragent-contracts")
@RequiredArgsConstructor
public class CounteragentContractController {

    private final CounteragentContractService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_READ + "')")
    public ResponseEntity<Page<CounteragentContractDto>> list(
            @RequestParam UUID counteragentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByCounteragent(counteragentId), page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_CREATE + "')")
    public ResponseEntity<CounteragentContractDto> create(@Valid @RequestBody CounteragentContractDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_UPDATE + "')")
    public ResponseEntity<CounteragentContractDto> update(@PathVariable UUID id, @Valid @RequestBody CounteragentContractDto request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('" + PermissionConstants.COUNTERAGENT_DELETE + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

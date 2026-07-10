package com.toir.controller.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartLifeRuleDto;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleRequest;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.sparepartlifecycle.SparePartLifeRuleService;
import com.toir.util.PaginationUtils;
import jakarta.validation.Valid;
import java.util.UUID;
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

@RestController
@RequestMapping("/api/v1/spare-part-life-rules")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class SparePartLifeRuleController {

    private final SparePartLifeRuleService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_LIFE_RULE_READ')")
    public ResponseEntity<Page<SparePartLifeRuleDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(PaginationUtils.page(service.list(), page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_LIFE_RULE_READ')")
    public ResponseEntity<SparePartLifeRuleDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_LIFE_RULE_CREATE')")
    public ResponseEntity<SparePartLifeRuleDto> create(@Valid @RequestBody SparePartLifeRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_LIFE_RULE_UPDATE')")
    public ResponseEntity<SparePartLifeRuleDto> revise(
            @PathVariable UUID id,
            @Valid @RequestBody SparePartLifeRuleRequest request
    ) {
        return ResponseEntity.ok(service.revise(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_LIFE_RULE_DELETE')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}

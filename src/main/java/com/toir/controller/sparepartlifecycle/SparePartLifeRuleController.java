package com.toir.controller.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.SparePartLifeRuleDto;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleFilter;
import com.toir.dto.sparepartlifecycle.SparePartLifeRuleRequest;
import com.toir.enums.sparepartlifecycle.SparePartLifeRuleScope;
import com.toir.exception.RestException;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.sparepartlifecycle.SparePartLifeRuleService;
import com.toir.util.PaginationUtils;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    /** Sort uchun ruxsat etilgan maydonlar (allowlist). Boshqa maydonlar 400 qaytaradi. */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "updatedAt", "createdAt", "revision", "effectiveFrom", "effectiveTo",
            "active", "scopeType", "name");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "updatedAt");

    private final SparePartLifeRuleService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_LIFE_RULE_READ')")
    public ResponseEntity<Page<SparePartLifeRuleDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID sparePartId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false) UUID equipmentNodeId,
            @RequestParam(required = false) SparePartLifeRuleScope scopeType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Instant effectiveAt,
            @RequestParam(required = false) String sort
    ) {
        SparePartLifeRuleFilter filter = new SparePartLifeRuleFilter(
                sparePartId, equipmentId, equipmentNodeId, scopeType, active, effectiveAt);
        Pageable pageable = PaginationUtils.pageRequest(page, size, resolveSort(sort));
        return ResponseEntity.ok(service.list(filter, pageable));
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

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw RestException.badRequest("Unsupported sort field: " + field);
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}

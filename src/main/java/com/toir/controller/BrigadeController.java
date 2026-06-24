package com.toir.controller;
import com.toir.dto.brigade.BrigadeDto;
import com.toir.dto.brigade.BrigadeMemberDto;
import com.toir.dto.brigade.BrigadeMemberRequest;
import com.toir.dto.brigade.BrigadeRequest;
import com.toir.service.BrigadeService;
import com.toir.util.PaginationUtils;
import com.toir.util.SortUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/brigades")
@Tag(name = "brigades")
@RequiredArgsConstructor
public class BrigadeController {

    private final BrigadeService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BRIGADE_READ')")
    public ResponseEntity<Page<BrigadeDto>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) String search
    , @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDir) {
        List<BrigadeDto> rows = service.findAll(departmentId, activeOnly, search);
        if ("memberCount".equals(sortBy)) {
            Comparator<BrigadeDto> comparator = Comparator.comparingInt(dto -> dto.members() == null ? 0 : dto.members().size());
            if (SortUtils.direction(sortDir, Sort.Direction.ASC).isDescending()) {
                comparator = comparator.reversed();
            }
            rows = rows.stream().sorted(comparator).toList();
        }
        return ResponseEntity.ok(PaginationUtils.page(rows, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BRIGADE_READ')")
    public ResponseEntity<BrigadeDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BRIGADE_CREATE')")
    public ResponseEntity<BrigadeDto> create(@Valid @RequestBody BrigadeRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BRIGADE_UPDATE')")
    public ResponseEntity<BrigadeDto> update(@PathVariable UUID id, @Valid @RequestBody BrigadeRequest r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BRIGADE_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BRIGADE_READ')")
    public ResponseEntity<Page<BrigadeMemberDto>> listMembers(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.listMembers(id), page, size));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BRIGADE_CREATE')")
    public ResponseEntity<BrigadeMemberDto> addMember(@PathVariable UUID id, @Valid @RequestBody BrigadeMemberRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addMember(id, r));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('BRIGADE_UPDATE')")
    public ResponseEntity<Void> removeMember(@PathVariable UUID id, @PathVariable UUID memberId) {
        service.removeMember(id, memberId);
        return ResponseEntity.noContent().build();
    }
}

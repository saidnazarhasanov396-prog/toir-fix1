package com.toir.controller;
import com.toir.dto.laborentry.LaborEntryDto;
import com.toir.service.LaborEntryService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "labor-entries")
@RequiredArgsConstructor
public class LaborEntryController {

    private final LaborEntryService service;

    @GetMapping("/work-orders/{workOrderId}/labor")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('WORK_ORDER_READ') or hasAuthority('TIMESHEET_READ')")
    public ResponseEntity<Page<LaborEntryDto>> list(@PathVariable UUID workOrderId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findByWorkOrder(workOrderId), page, size));
    }

    @PostMapping("/work-orders/{workOrderId}/labor")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('TIMESHEET_CREATE')")
    public ResponseEntity<LaborEntryDto> create(@PathVariable UUID workOrderId, @Valid @RequestBody LaborEntryDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workOrderId, r));
    }

    @PutMapping("/labor-entries/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('TIMESHEET_UPDATE')")
    public ResponseEntity<LaborEntryDto> update(@PathVariable UUID id, @Valid @RequestBody LaborEntryDto r) {
        return ResponseEntity.ok(service.update(id, r));
    }

    @DeleteMapping("/labor-entries/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('TIMESHEET_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

package com.toir.controller.department;
import com.toir.dto.department.DepartmentDto;
import com.toir.dto.department.DepartmentRequest;
import com.toir.dto.hr.EmployeeDto;
import com.toir.enums.DepartmentType;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.department.DepartmentService;
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
@RequestMapping("/api/v1/departments")
@Tag(name = "departments")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEPARTMENT_READ')")
    public ResponseEntity<Page<DepartmentDto>> list(
            @RequestParam(required = false) DepartmentType type,
            @RequestParam(required = false, defaultValue = "") String search
    , @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.findAll(type,search), page, size));
    }

    @GetMapping("/{departmentId}/employees")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEPARTMENT_READ')")
    public ResponseEntity<List<EmployeeDto>> getEmployeesByDepartment(
            @PathVariable UUID departmentId
    ) {
        return ResponseEntity.ok(service.findEmployeesByDepartment(departmentId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEPARTMENT_READ')")
    public ResponseEntity<DepartmentDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEPARTMENT_CREATE')")
    public ResponseEntity<DepartmentDto> create(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEPARTMENT_UPDATE')")
    public ResponseEntity<DepartmentDto> update(@PathVariable UUID id, @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('DEPARTMENT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

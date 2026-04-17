package com.toir.repairrequest;

import com.toir.common.security.SecurityScope;
import com.toir.repairrequest.dto.CloseRequestRequest;
import com.toir.repairrequest.dto.RepairRequestDto;
import com.toir.repairrequest.dto.RepairRequestRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/repair-requests")
@Tag(name = "repair-requests")
public class RepairRequestController {

    private final RepairRequestService service;
    private final SecurityScope securityScope;

    public RepairRequestController(RepairRequestService service, SecurityScope securityScope) {
        this.service = service;
        this.securityScope = securityScope;
    }

    @GetMapping
    public List<RepairRequestDto> list(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId
    ) {
        return service.search(status, securityScope.enforceDepartmentScope(departmentId), equipmentId);
    }

    @GetMapping("/{id}")
    public RepairRequestDto get(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<RepairRequestDto> create(@Valid @RequestBody RepairRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/status")
    public RepairRequestDto changeStatus(@PathVariable UUID id, @RequestParam RequestStatus status) {
        return service.changeStatus(id, status);
    }

    @PostMapping("/{id}/close")
    public RepairRequestDto close(@PathVariable UUID id, @Valid @RequestBody CloseRequestRequest request) {
        return service.close(id, request);
    }

    @PostMapping("/{id}/assign")
    public RepairRequestDto assign(@PathVariable UUID id, @RequestParam UUID assigneeId) {
        return service.assign(id, assigneeId);
    }

    @PostMapping("/{id}/reject")
    public RepairRequestDto reject(@PathVariable UUID id, @RequestParam String reason) {
        return service.reject(id, reason);
    }

    @PostMapping("/{id}/request-clarification")
    public RepairRequestDto requestClarification(@PathVariable UUID id, @RequestParam String comment) {
        return service.requestClarification(id, comment);
    }
}

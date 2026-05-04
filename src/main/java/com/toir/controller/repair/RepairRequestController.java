package com.toir.controller.repair;
import com.toir.dto.repairrequest.CloseRequestRequest;
import com.toir.dto.repairrequest.RepairRequestDto;
import com.toir.dto.repairrequest.RepairRequestRequest;
import com.toir.enums.PriorityLevel;
import com.toir.enums.RequestStatus;
import com.toir.security.SecurityScope;
import com.toir.service.repair.RepairRequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Page<RepairRequestDto>> list(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID equipmentId,
            @RequestParam(required = false)PriorityLevel priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(service.search(status, securityScope.enforceDepartmentScope(departmentId), equipmentId,priority, page, size, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepairRequestDto> get(@PathVariable UUID id) { return ResponseEntity.ok(service.findById(id)); }

    @PostMapping
    public ResponseEntity<RepairRequestDto> create(@Valid @RequestBody RepairRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<RepairRequestDto> changeStatus(@PathVariable UUID id, @RequestParam RequestStatus status) {
        return ResponseEntity.ok(service.changeStatus(id, status));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<RepairRequestDto> close(@PathVariable UUID id, @Valid @RequestBody CloseRequestRequest request) {
        return ResponseEntity.ok(service.close(id, request));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<RepairRequestDto> assign(@PathVariable UUID id, @RequestParam UUID assigneeId) {
        return ResponseEntity.ok(service.assign(id, assigneeId));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<RepairRequestDto> reject(@PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(service.reject(id, reason));
    }

    @PostMapping("/{id}/request-clarification")
    public ResponseEntity<RepairRequestDto> requestClarification(@PathVariable UUID id, @RequestParam String comment) {
        return ResponseEntity.ok(service.requestClarification(id, comment));
    }
}

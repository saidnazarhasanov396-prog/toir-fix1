package com.toir.controller;
import com.toir.dto.inspection.*;
import com.toir.dto.inspection.InspectionRoundDto;
import com.toir.dto.inspection.InspectionRoundResultDto;
import com.toir.dto.inspection.InspectionRoundResultRequest;
import com.toir.dto.inspection.InspectionRouteDto;
import com.toir.dto.inspection.InspectionRouteRequest;
import com.toir.enums.InspectionRoundStatus;
import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.service.InspectionService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "inspection")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService service;
    private final SecurityScope securityScope;

    // routes
    @GetMapping("/inspection-routes")
    public List<InspectionRouteDto> listRoutes(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search
    ) {
        return service.findRoutes(departmentId, active,search);
    }

    @GetMapping("/inspection-routes/{id}")
    public ResponseEntity<InspectionRouteDto> getRoute(@PathVariable UUID id) { return ResponseEntity.ok(service.getRoute(id)); }

    @PostMapping("/inspection-routes")
    public ResponseEntity<InspectionRouteDto> createRoute(@Valid @RequestBody InspectionRouteRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRoute(r));
    }

    @PutMapping("/inspection-routes/{id}")
    public ResponseEntity<InspectionRouteDto> updateRoute(@PathVariable UUID id, @Valid @RequestBody InspectionRouteRequest r) {
        return ResponseEntity.ok(service.updateRoute(id, r));
    }

    @DeleteMapping("/inspection-routes/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable UUID id) {
        service.deleteRoute(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inspection-routes/{id}/checkpoints")
    public ResponseEntity<InspectionRouteDto> addCheckpoint(@PathVariable UUID id,
                                            @Valid @RequestBody InspectionRouteRequest.CheckpointRequest cp) {
        return ResponseEntity.ok(service.addCheckpoint(id, cp));
    }

    // rounds
    @GetMapping("/inspection-rounds")
    public ResponseEntity<Page<InspectionRoundDto>> listRounds(
             @RequestParam(required = false) UUID routeId,
             @RequestParam(required = false) UUID performedBy,
             @RequestParam(required = false) InspectionRoundStatus status
    , @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PaginationUtils.page(service.listRounds(routeId, performedBy,status), page, size));
    }

    @GetMapping("/inspection-rounds/{id}")
    public ResponseEntity<InspectionRoundDto> getRound(@PathVariable UUID id) { return ResponseEntity.ok(service.getRound(id)); }

    @PostMapping("/inspection-routes/{routeId}/start")
    public ResponseEntity<InspectionRoundDto> startRound(@PathVariable UUID routeId) {
        AuthenticatedUser u = securityScope.currentUser();
        UUID userId = u != null && u.id() != null ? UUID.fromString(u.id()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(service.startRound(routeId, userId));
    }

    @PostMapping("/inspection-rounds/{id}/results")
    public ResponseEntity<InspectionRoundResultDto> addResult(@PathVariable UUID id,
                                              @Valid @RequestBody InspectionRoundResultRequest r) {
        return ResponseEntity.ok(service.recordResult(id, r));
    }

    @PostMapping("/inspection-rounds/{id}/complete")
    public ResponseEntity<InspectionRoundDto> complete(@PathVariable UUID id, @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(service.completeRound(id, notes));
    }

    @PostMapping("/inspection-rounds/{id}/cancel")
    public ResponseEntity<InspectionRoundDto> cancel(@PathVariable UUID id, @RequestParam String reason) {
        return ResponseEntity.ok(service.cancelRound(id, reason));
    }
}

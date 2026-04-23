package com.toir.controller;
import com.toir.dto.inspection.InspectionRoundDto;
import com.toir.dto.inspection.InspectionRoundResultDto;
import com.toir.dto.inspection.InspectionRoundResultRequest;
import com.toir.dto.inspection.InspectionRouteDto;
import com.toir.dto.inspection.InspectionRouteRequest;
import com.toir.service.InspectionService;

import com.toir.security.AuthenticatedUser;
import com.toir.security.SecurityScope;
import com.toir.dto.inspection.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "inspection")
public class InspectionController {

    private final InspectionService service;
    private final SecurityScope securityScope;

    public InspectionController(InspectionService service, SecurityScope securityScope) {
        this.service = service;
        this.securityScope = securityScope;
    }

    // routes
    @GetMapping("/inspection-routes")
    public List<InspectionRouteDto> listRoutes(@RequestParam(required = false) UUID departmentId,
                                               @RequestParam(required = false) Boolean activeOnly) {
        return service.findRoutes(departmentId, activeOnly);
    }

    @GetMapping("/inspection-routes/{id}")
    public InspectionRouteDto getRoute(@PathVariable UUID id) { return service.getRoute(id); }

    @PostMapping("/inspection-routes")
    public ResponseEntity<InspectionRouteDto> createRoute(@Valid @RequestBody InspectionRouteRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRoute(r));
    }

    @PutMapping("/inspection-routes/{id}")
    public InspectionRouteDto updateRoute(@PathVariable UUID id, @Valid @RequestBody InspectionRouteRequest r) {
        return service.updateRoute(id, r);
    }

    @DeleteMapping("/inspection-routes/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable UUID id) {
        service.deleteRoute(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inspection-routes/{id}/checkpoints")
    public InspectionRouteDto addCheckpoint(@PathVariable UUID id,
                                            @Valid @RequestBody InspectionRouteRequest.CheckpointRequest cp) {
        return service.addCheckpoint(id, cp);
    }

    // rounds
    @GetMapping("/inspection-rounds")
    public List<InspectionRoundDto> listRounds(@RequestParam(required = false) UUID routeId,
                                               @RequestParam(required = false) UUID performedBy) {
        return service.listRounds(routeId, performedBy);
    }

    @GetMapping("/inspection-rounds/{id}")
    public InspectionRoundDto getRound(@PathVariable UUID id) { return service.getRound(id); }

    @PostMapping("/inspection-routes/{routeId}/start")
    public ResponseEntity<InspectionRoundDto> startRound(@PathVariable UUID routeId) {
        AuthenticatedUser u = securityScope.currentUser();
        UUID userId = u != null && u.id() != null ? UUID.fromString(u.id()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(service.startRound(routeId, userId));
    }

    @PostMapping("/inspection-rounds/{id}/results")
    public InspectionRoundResultDto addResult(@PathVariable UUID id,
                                              @Valid @RequestBody InspectionRoundResultRequest r) {
        return service.recordResult(id, r);
    }

    @PostMapping("/inspection-rounds/{id}/complete")
    public InspectionRoundDto complete(@PathVariable UUID id, @RequestParam(required = false) String notes) {
        return service.completeRound(id, notes);
    }

    @PostMapping("/inspection-rounds/{id}/cancel")
    public InspectionRoundDto cancel(@PathVariable UUID id, @RequestParam String reason) {
        return service.cancelRound(id, reason);
    }
}

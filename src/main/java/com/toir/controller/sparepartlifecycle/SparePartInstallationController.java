package com.toir.controller.sparepartlifecycle;

import com.toir.dto.sparepartlifecycle.InstallSparePartCommand;
import com.toir.dto.sparepartlifecycle.RemoveSparePartCommand;
import com.toir.dto.sparepartlifecycle.ReplaceSparePartCommand;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleResult;
import com.toir.dto.sparepartlifecycle.SparePartLifecycleEvaluation;
import com.toir.dto.sparepartlifecycle.MarkSparePartManualDueRequest;
import com.toir.entity.sparepartlifecycle.SparePartInstallation;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.security.ScopeAccessService;
import com.toir.service.sparepartlifecycle.SparePartLifecycleService;
import com.toir.service.sparepartlifecycle.SparePartLifecycleEvaluationService;
import java.time.Instant;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spare-part-installations")
@RequiresSensitiveAccess
@RequiredArgsConstructor
public class SparePartInstallationController {

    private final SparePartLifecycleService service;
    private final SparePartLifecycleEvaluationService evaluationService;
    private final ScopeAccessService scopeAccessService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_INSTALLATION_READ')")
    public ResponseEntity<SparePartInstallation> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping("/equipment/{equipmentId}/current")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_INSTALLATION_READ')")
    public ResponseEntity<List<SparePartInstallation>> current(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.current(equipmentId));
    }

    @GetMapping("/equipment/{equipmentId}/history")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_INSTALLATION_READ')")
    public ResponseEntity<List<SparePartInstallation>> history(@PathVariable UUID equipmentId) {
        return ResponseEntity.ok(service.history(equipmentId));
    }

    @PostMapping("/equipment/{equipmentId}")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_INSTALL')")
    public ResponseEntity<SparePartLifecycleResult> install(
            @PathVariable UUID equipmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InstallSparePartCommand request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.install(equipmentId, idempotencyKey, scopeAccessService.currentUserIdOrNull(), request));
    }

    @PostMapping("/{id}/remove")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_REMOVE')")
    public ResponseEntity<SparePartLifecycleResult> remove(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RemoveSparePartCommand request
    ) {
        RemoveSparePartCommand scoped = new RemoveSparePartCommand(
                id, request.removedAt(), request.workOrderId(), request.disposition(), request.reason(), request.notes());
        return ResponseEntity.ok(service.remove(idempotencyKey, scopeAccessService.currentUserIdOrNull(), scoped));
    }

    @PostMapping("/{id}/replace")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_REPLACE')")
    public ResponseEntity<SparePartLifecycleResult> replace(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReplaceSparePartCommand request
    ) {
        ReplaceSparePartCommand scoped = new ReplaceSparePartCommand(
                id, request.newPart(), request.replacedAt(), request.workOrderId(),
                request.oldPartDisposition(), request.reason(), request.notes());
        return ResponseEntity.ok(service.replace(idempotencyKey, scopeAccessService.currentUserIdOrNull(), scoped));
    }

    @PostMapping("/{id}/reevaluate")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('SPARE_PART_EXPIRY_OVERRIDE')")
    public ResponseEntity<SparePartLifecycleEvaluation> reevaluate(@PathVariable UUID id) {
        return ResponseEntity.ok(evaluationService.reevaluate(id, Instant.now()));
    }

    @PostMapping("/{id}/manual-due")
    @PreAuthorize("hasAuthority('*') or hasAuthority('SPARE_PART_MANUAL_DUE')")
    public ResponseEntity<SparePartLifecycleEvaluation> manualDue(
            @PathVariable UUID id,
            @Valid @RequestBody MarkSparePartManualDueRequest request
    ) {
        return ResponseEntity.ok(evaluationService.markManualDue(
                id, scopeAccessService.currentUserIdOrNull(), request.reason()));
    }
}

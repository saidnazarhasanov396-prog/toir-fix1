package com.toir.controller;

import com.toir.dto.maintenanceschedule.MaintenanceScheduleCalculationRequest;
import com.toir.dto.pprplanning.PprPlanningSelectionRequest;
import com.toir.dto.pprplanning.PprPlanningSessionCreateRequest;
import com.toir.dto.pprplanning.PprPlanningSessionDto;
import com.toir.dto.pprplanning.PprPlanningSubmitRequest;
import com.toir.dto.approval.ApprovalRequestDto;
import com.toir.dto.approval.ApprovalStartRequest;
import com.toir.dto.pprplanning.PprPlanningVariantRequest;
import com.toir.entity.planning.PprPlanningSession;
import com.toir.entity.planning.PprPlanningVariantItem;
import com.toir.exception.RestException;
import com.toir.enums.ApprovalActionType;
import com.toir.enums.ApprovalTargetType;
import com.toir.service.ApprovalService;
import com.toir.service.planning.PprPlanningSessionService;
import com.toir.service.planning.PprPlanningIdempotencyService;
import com.toir.service.planning.PprPlanningVariantService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "toir.ppr-lifecycle", name = "planning-sessions-enabled", havingValue = "true")
@RequestMapping("/api/ppr-planning-sessions")
@RequiredArgsConstructor
public class PprPlanningSessionController {

    private static final String READ =
            "hasAnyAuthority('PPR_PLAN_READ','PPR_CALENDAR_READ','SYSTEM_ADMIN','*')";
    private static final String CREATE =
            "hasAnyAuthority('PPR_PLAN_CREATE','SYSTEM_ADMIN','*')";
    private static final String UPDATE =
            "hasAnyAuthority('PPR_PLAN_UPDATE','PPR_PLAN_GENERATE','SYSTEM_ADMIN','*')";

    private final PprPlanningSessionService sessionService;
    private final PprPlanningVariantService variantService;
    private final PprPlanningIdempotencyService idempotencyService;
    @Autowired
    private ApprovalService approvalService;

    @PostMapping
    @PreAuthorize(CREATE)
    public ResponseEntity<PprPlanningSessionDto> create(
            @Valid @RequestBody PprPlanningSessionCreateRequest request) {
        PprPlanningSession created = sessionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dto(created));
    }

    @GetMapping
    @PreAuthorize(READ)
    public List<PprPlanningSessionDto> list() {
        return sessionService.list().stream().map(this::dto).toList();
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize(READ)
    public PprPlanningSessionDto get(@PathVariable UUID sessionId) {
        return dto(sessionService.get(sessionId));
    }

    @PostMapping("/{sessionId}/variants")
    @PreAuthorize(UPDATE)
    public ResponseEntity<PprPlanningSessionDto.Variant> createVariant(
            @PathVariable UUID sessionId,
            @Valid @RequestBody PprPlanningVariantRequest request) {
        var variant = sessionService.createVariant(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PprPlanningSessionDto.Variant.from(variant));
    }

    @PostMapping("/{sessionId}/variants/{variantId}/copy")
    @PreAuthorize(UPDATE)
    public ResponseEntity<PprPlanningSessionDto.Variant> copyVariant(
            @PathVariable UUID sessionId,
            @PathVariable UUID variantId,
            @Valid @RequestBody PprPlanningVariantRequest request) {
        var variant = sessionService.copyVariant(sessionId, variantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PprPlanningSessionDto.Variant.from(variant));
    }

    @PatchMapping("/{sessionId}/variants/{variantId}")
    @PreAuthorize(UPDATE)
    public PprPlanningSessionDto.Variant renameVariant(
            @PathVariable UUID sessionId,
            @PathVariable UUID variantId,
            @Valid @RequestBody PprPlanningVariantRequest request) {
        return PprPlanningSessionDto.Variant.from(
                sessionService.renameVariant(sessionId, variantId, request));
    }

    @PostMapping("/{sessionId}/variants/{variantId}/calculate")
    @PreAuthorize(UPDATE)
    public PprPlanningSessionDto.Variant calculate(
            @PathVariable UUID sessionId,
            @PathVariable UUID variantId,
            @Valid @RequestBody MaintenanceScheduleCalculationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        return idempotencyService.execute(
                sessionId,
                "CALCULATE:" + variantId,
                idempotencyKey,
                request,
                PprPlanningSessionDto.Variant.class,
                () -> PprPlanningSessionDto.Variant.from(
                        variantService.calculate(sessionId, variantId, request)));
    }

    @GetMapping("/{sessionId}/variants/{variantId}/revisions/{revision}")
    @PreAuthorize(READ)
    public List<PprPlanningVariantItem> revision(
            @PathVariable UUID sessionId,
            @PathVariable UUID variantId,
            @PathVariable long revision) {
        return sessionService.revision(sessionId, variantId, revision);
    }

    @PostMapping("/{sessionId}/select")
    @PreAuthorize(UPDATE)
    public PprPlanningSessionDto select(
            @PathVariable UUID sessionId,
            @Valid @RequestBody PprPlanningSelectionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        return idempotencyService.execute(
                sessionId,
                "SELECT",
                idempotencyKey,
                request,
                PprPlanningSessionDto.class,
                () -> dto(variantService.select(
                        sessionId, request.variantId(), request)));
    }

    @PostMapping("/{sessionId}/submit")
    @PreAuthorize("hasAnyAuthority('PPR_PLAN_UPDATE','APPROVAL_CREATE','SYSTEM_ADMIN','*')")
    public ApprovalRequestDto submit(
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) PprPlanningSubmitRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        return idempotencyService.execute(
                sessionId,
                "SUBMIT",
                idempotencyKey,
                request,
                ApprovalRequestDto.class,
                () -> approvalService.requestApproval(new ApprovalStartRequest(
                        ApprovalTargetType.PPR_PLANNING_SESSION,
                        sessionId,
                        ApprovalActionType.APPROVE,
                        request == null ? null : request.comment())));
    }

    private PprPlanningSessionDto dto(PprPlanningSession session) {
        return PprPlanningSessionDto.from(session, sessionService.variants(session.getId()));
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw RestException.badRequest("IDEMPOTENCY_KEY_REQUIRED: Idempotency-Key is required");
        }
    }
}

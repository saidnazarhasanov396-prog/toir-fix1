package com.toir.controller;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideResponseDto;
import com.toir.dto.financialreview.*;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.PermissionConstants;
import com.toir.service.ActualCostReviewFacadeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/budgets/actual-costs")
@Tag(name = "actual-cost-review-actions")
@RequiredArgsConstructor
public class ActualCostReviewActionController {

    private static final String READ_AUTH = "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_READ')";
    private static final String APPROVE_AUTH = "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_APPROVE')";
    private static final String REJECT_AUTH = "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('ACTUAL_COST_REJECT')";
    private static final String OVERRIDE_APPLY_AUTH = "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_APPLY')";
    private static final String OVERRIDE_CLEAR_AUTH = "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_ROUTE_OVERRIDE_CLEAR')";

    private final ActualCostReviewFacadeService service;

    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE_AUTH)
    public ResponseEntity<ActualCostDto> approve(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user,
            @RequestBody(required = false) ReviewRequest request) {
        return ResponseEntity.ok(service.approve(id, currentUserId(user), request != null ? request.reviewComment() : null));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(REJECT_AUTH)
    public ResponseEntity<ActualCostDto> reject(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user,
            @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(service.reject(id, currentUserId(user), request != null ? request.reviewComment() : null));
    }

    @PostMapping("/bulk-review")
    @PreAuthorize(APPROVE_AUTH + " or hasAuthority('ACTUAL_COST_REJECT')")
    public ResponseEntity<BulkActualCostReviewResponse> bulkReview(
            @CurrentUser AuthenticatedUser user,
            @RequestBody BulkReviewRequest request) {
        return ResponseEntity.ok(service.bulkReview(
                request != null ? request.ids() : List.of(),
                request != null ? request.action() : "APPROVE",
                currentUserId(user),
                request != null ? request.reviewComment() : null
        ));
    }

    @PostMapping("/evaluate-overdue")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<EvaluateOverdueActualCostsResponse> evaluateOverdue(
            @RequestBody(required = false) EvaluateOverdueRequest request) {
        return ResponseEntity.ok(service.evaluateOverdue(
                request != null ? request.thresholdHours() : null,
                request != null ? request.reminderWindowHours() : null
        ));
    }

    @PostMapping("/bulk-sla-action")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<BulkActualCostSlaActionResponse> bulkSlaAction(
            @CurrentUser AuthenticatedUser user,
            @RequestBody BulkSlaActionRequest request) {
        return ResponseEntity.ok(service.bulkSlaAction(
                request != null ? request.ids() : List.of(),
                request != null ? request.action() : "REMIND",
                request != null ? request.thresholdHours() : null,
                request != null ? request.reminderWindowHours() : null,
                currentUserId(user),
                request != null ? request.comment() : null
        ));
    }

    @PostMapping("/{id}/review-route-override")
    @PreAuthorize(OVERRIDE_APPLY_AUTH)
    public ResponseEntity<ActualCostReviewRouteOverrideResponseDto> applyRouteOverride(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user,
            @RequestBody RouteOverrideRequest request) {
        return ResponseEntity.ok(service.applyRouteOverride(
                id,
                request != null ? request.departmentId() : null,
                request != null ? request.approvalRoleCode() : null,
                request != null ? request.escalationRoleCode() : null,
                request != null ? request.thresholdHours() : null,
                request != null ? request.comment() : null,
                currentUserId(user)
        ));
    }

    @PostMapping("/{id}/review-route-override/clear")
    @PreAuthorize(OVERRIDE_CLEAR_AUTH)
    public ResponseEntity<BulkRouteOverrideClearResponse.Success> clearRouteOverride(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user,
            @RequestBody CommentRequest request) {
        List<UUID> cleared = service.clearRouteOverride(id, currentUserId(user), request != null ? request.comment() : null);
        return ResponseEntity.ok(new BulkRouteOverrideClearResponse.Success(id, cleared));
    }

    @PostMapping("/{id}/inbox-handover")
    @PreAuthorize(OVERRIDE_APPLY_AUTH)
    public ResponseEntity<ActualCostReviewInboxHandoverResponse> handoverFromInbox(
            @PathVariable UUID id,
            @CurrentUser AuthenticatedUser user,
            @RequestBody InboxHandoverRequest request) {
        return ResponseEntity.ok(service.handoverFromInbox(
                id,
                request.notificationId(),
                request.departmentId(),
                request.approvalRoleCode(),
                request.escalationRoleCode(),
                request.thresholdHours(),
                request.handoverComment(),
                request.acknowledgementComment(),
                currentUserId(user),
                isScopeAdmin(user)
        ));
    }

    @PostMapping("/inbox-handover/bulk")
    @PreAuthorize(OVERRIDE_APPLY_AUTH)
    public ResponseEntity<BulkActualCostReviewInboxHandoverResponse> bulkHandoverFromInbox(
            @CurrentUser AuthenticatedUser user,
            @RequestBody BulkInboxHandoverRequest request) {
        List<BulkActualCostReviewInboxHandoverResponse.Success> successes = new ArrayList<>();
        List<BulkActualCostReviewInboxHandoverResponse.Failure> failures = new ArrayList<>();
        List<UUID> notificationIds = request != null ? request.notificationIds() : List.of();
        UUID actorId = currentUserId(user);
        boolean scopeAdmin = isScopeAdmin(user);
        for (UUID notificationId : notificationIds) {
            try {
                UUID actualCostId = service.actualCostIdFromNotification(notificationId, actorId, scopeAdmin);
                ActualCostReviewInboxHandoverResponse response = service.handoverFromInbox(
                        actualCostId,
                        notificationId,
                        request.departmentId(),
                        request.approvalRoleCode(),
                        request.escalationRoleCode(),
                        request.thresholdHours(),
                        request.handoverComment(),
                        request.acknowledgementComment(),
                        actorId,
                        scopeAdmin
                );
                successes.add(new BulkActualCostReviewInboxHandoverResponse.Success(
                        notificationId,
                        actualCostId,
                        response.override().id(),
                        response.acknowledgedNotificationId()
                ));
            } catch (RuntimeException ex) {
                failures.add(new BulkActualCostReviewInboxHandoverResponse.Failure(notificationId, null, ex.getMessage()));
            }
        }
        return ResponseEntity.ok(new BulkActualCostReviewInboxHandoverResponse(
                "HANDOVER",
                notificationIds.size(),
                successes.size(),
                failures.size(),
                successes,
                failures
        ));
    }

    @GetMapping(value = "/review-queue/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<String> exportReviewQueue(@RequestParam(required = false) String search) {
        return csv("actual-cost-review-queue.csv", service.csv("actual-cost-review-queue.csv", service.reviewQueue(search)));
    }

    @GetMapping(value = "/review-history-pack/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<String> exportReviewHistoryPack(@RequestParam(required = false) String search) {
        return csv("actual-cost-review-history-pack.csv", service.csv("actual-cost-review-history-pack.csv", service.actualCostRegister(search)));
    }

    @GetMapping(value = "/approval-pack/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<String> exportApprovalPack(@RequestParam(required = false) String search) {
        return csv("actual-cost-review-approval-pack.csv", service.csv("actual-cost-review-approval-pack.csv", service.reviewQueue(search)));
    }

    @GetMapping(value = "/review-activity/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<String> exportReviewActivity(@RequestParam(required = false) String search) {
        return csv("actual-cost-review-activity.csv", service.activityCsv(service.activity(search)));
    }

    @GetMapping(value = "/handovers/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<String> exportHandovers(@RequestParam(required = false) String search) {
        return csv("actual-cost-review-handovers.csv", service.handoversCsv(service.handovers(search)));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<String> exportActualCosts(@RequestParam(required = false) String search) {
        return csv("actual-costs.csv", service.csv("actual-costs.csv", service.actualCostRegister(search)));
    }

    private ResponseEntity<String> csv(String filename, String content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }

    private UUID currentUserId(AuthenticatedUser user) {
        return user != null ? UUID.fromString(user.id()) : new UUID(0, 0);
    }

    private boolean isScopeAdmin(AuthenticatedUser user) {
        return user == null
                || "SYSTEM_ADMIN".equals(user.primaryRoleCode())
                || (user.permissions() != null && user.permissions().contains(PermissionConstants.WILDCARD));
    }

    public record ReviewRequest(String reviewComment) {
    }

    public record BulkReviewRequest(List<UUID> ids, String action, String reviewComment) {
    }

    public record EvaluateOverdueRequest(Integer thresholdHours, Integer reminderWindowHours) {
    }

    public record BulkSlaActionRequest(List<UUID> ids, String action, Integer thresholdHours,
                                       Integer reminderWindowHours, String comment) {
    }

    public record RouteOverrideRequest(UUID departmentId, String approvalRoleCode, String escalationRoleCode,
                                       Integer thresholdHours, String comment) {
    }

    public record CommentRequest(String comment) {
    }

    public record InboxHandoverRequest(UUID notificationId, UUID departmentId, String approvalRoleCode,
                                       String escalationRoleCode, Integer thresholdHours, String handoverComment,
                                       String acknowledgementComment) {
    }

    public record BulkInboxHandoverRequest(List<UUID> notificationIds, UUID departmentId, String approvalRoleCode,
                                           String escalationRoleCode, Integer thresholdHours, String handoverComment,
                                           String acknowledgementComment) {
    }
}

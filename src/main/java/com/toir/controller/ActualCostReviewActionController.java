package com.toir.controller;

import com.toir.dto.actualcost.ActualCostDto;
import com.toir.dto.actualcostrouteoverride.ActualCostReviewRouteOverrideResponseDto;
import com.toir.dto.financialreview.*;
import com.toir.exception.RestException;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private static final String EXPORT_AUTH = "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('FINANCE_REPORT_EXPORT')";

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
    @PreAuthorize(EXPORT_AUTH)
    public ResponseEntity<String> exportReviewQueue(@RequestParam(required = false) String search,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Boolean overdueOnly,
                                                    @RequestParam(required = false) Boolean myQueue,
                                                    @RequestParam(required = false) String attentionMode,
                                                    @RequestParam(required = false) Integer reminderWindowHours,
                                                    @RequestParam(required = false) String approvalRoleCode,
                                                    @RequestParam(required = false) UUID departmentId,
                                                    @RequestParam(required = false) UUID counteragentId,
                                                    @RequestParam(required = false) UUID actualCostId,
                                                    @RequestParam(required = false) String actualCostIds,
                                                    @RequestParam(required = false) String allocationStatus) {
        return csv("actual-cost-review-queue.csv",
                service.csv("actual-cost-review-queue.csv", filterReviewItems(
                        service.reviewQueue(search),
                        status,
                        Boolean.TRUE.equals(overdueOnly),
                        approvalRoleCode,
                        null,
                        departmentId,
                        counteragentId,
                        attentionMode,
                        reminderWindowHours,
                        null,
                        null,
                        actualCostId,
                        actualCostIds,
                        myQueue,
                        allocationStatus
                )));
    }

    @GetMapping(value = "/review-history-pack/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(EXPORT_AUTH)
    public ResponseEntity<String> exportReviewHistoryPack(@RequestParam(required = false) String search,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) UUID costCategoryId,
                                                          @RequestParam(required = false) UUID departmentId,
                                                          @RequestParam(required = false) UUID counteragentId,
                                                          @RequestParam(required = false) String dateFrom,
                                                          @RequestParam(required = false) String dateTo,
                                                          @RequestParam(required = false) UUID actualCostId,
                                                          @RequestParam(required = false) String actualCostIds,
                                                          @RequestParam(required = false) String allocationStatus) {
        return csv("actual-cost-review-history-pack.csv",
                service.csv("actual-cost-review-history-pack.csv", filterReviewItems(
                        service.actualCostRegister(search),
                        status,
                        null,
                        null,
                        costCategoryId,
                        departmentId,
                        counteragentId,
                        null,
                        null,
                        parseDateStart(dateFrom),
                        parseDateEnd(dateTo),
                        actualCostId,
                        actualCostIds,
                        null,
                        allocationStatus
                )));
    }

    @GetMapping(value = "/approval-pack/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(EXPORT_AUTH)
    public ResponseEntity<String> exportApprovalPack(@RequestParam(required = false) String search,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Boolean overdueOnly,
                                                     @RequestParam(required = false) Boolean myQueue,
                                                     @RequestParam(required = false) String attentionMode,
                                                     @RequestParam(required = false) Integer reminderWindowHours,
                                                     @RequestParam(required = false) String approvalRoleCode,
                                                     @RequestParam(required = false) UUID departmentId,
                                                     @RequestParam(required = false) UUID counteragentId,
                                                     @RequestParam(required = false) UUID actualCostId,
                                                     @RequestParam(required = false) String actualCostIds,
                                                     @RequestParam(required = false) String allocationStatus) {
        return csv("actual-cost-review-approval-pack.csv",
                service.csv("actual-cost-review-approval-pack.csv", filterReviewItems(
                        service.reviewQueue(search),
                        status,
                        Boolean.TRUE.equals(overdueOnly),
                        approvalRoleCode,
                        null,
                        departmentId,
                        counteragentId,
                        attentionMode,
                        reminderWindowHours,
                        null,
                        null,
                        actualCostId,
                        actualCostIds,
                        myQueue,
                        allocationStatus
                )));
    }

    @GetMapping(value = "/review-activity/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(EXPORT_AUTH)
    public ResponseEntity<String> exportReviewActivity(@RequestParam(required = false) String search,
                                                       @RequestParam(required = false) String eventGroup,
                                                       @RequestParam(required = false) UUID departmentId,
                                                       @RequestParam(required = false) String roleCode,
                                                       @RequestParam(required = false) UUID actualCostId,
                                                       @RequestParam(required = false) String actualCostIds) {
        return csv("actual-cost-review-activity.csv", service.activityCsv(filterActivityItems(
                service.activity(search),
                eventGroup,
                departmentId,
                roleCode,
                actualCostId,
                actualCostIds
        )));
    }

    @GetMapping(value = "/handovers/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(EXPORT_AUTH)
    public ResponseEntity<String> exportHandovers(@RequestParam(required = false) String search,
                                                  @RequestParam(required = false) UUID departmentId,
                                                  @RequestParam(required = false) String approvalRoleCode,
                                                  @RequestParam(required = false) UUID actualCostId,
                                                  @RequestParam(required = false) String actualCostIds) {
        return csv("actual-cost-review-handovers.csv", service.handoversCsv(filterHandoverItems(
                service.handovers(search),
                departmentId,
                approvalRoleCode,
                actualCostId,
                actualCostIds
        )));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @PreAuthorize(EXPORT_AUTH)
    public ResponseEntity<String> exportActualCosts(@RequestParam(required = false) String search,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) UUID costCategoryId,
                                                    @RequestParam(required = false) UUID departmentId,
                                                    @RequestParam(required = false) UUID counteragentId,
                                                    @RequestParam(required = false) String dateFrom,
                                                    @RequestParam(required = false) String dateTo,
                                                    @RequestParam(required = false) UUID actualCostId,
                                                    @RequestParam(required = false) String actualCostIds,
                                                    @RequestParam(required = false) String allocationStatus) {
        return csv("actual-costs.csv",
                service.csv("actual-costs.csv", filterReviewItems(
                        service.actualCostRegister(search),
                        status,
                        null,
                        null,
                        costCategoryId,
                        departmentId,
                        counteragentId,
                        null,
                        null,
                        parseDateStart(dateFrom),
                        parseDateEnd(dateTo),
                        actualCostId,
                        actualCostIds,
                        null,
                        allocationStatus
                )));
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

    private List<ActualCostReviewItem> filterReviewItems(List<ActualCostReviewItem> items,
                                                         String status,
                                                         Boolean overdueOnly,
                                                         String approvalRoleCode,
                                                         UUID costCategoryId,
                                                         UUID departmentId,
                                                         UUID counteragentId,
                                                         String attentionMode,
                                                         Integer reminderWindowHours,
                                                         Instant dateFrom,
                                                         Instant dateTo,
                                                         UUID actualCostId,
                                                         String actualCostIds,
                                                         Boolean myQueue,
                                                         String allocationStatus) {
        Set<UUID> scopedIds = parseActualCostIds(actualCostId, actualCostIds);
        return items.stream()
                .filter(item -> status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                        || status.equalsIgnoreCase(item.status()))
                .filter(item -> overdueOnly == null || !overdueOnly || item.isOverdue())
                .filter(item -> approvalRoleCode == null || approvalRoleCode.isBlank()
                        || approvalRoleCode.equalsIgnoreCase(item.approvalRoleCode()))
                .filter(item -> costCategoryId == null || costCategoryId.equals(item.costCategoryId()))
                .filter(item -> departmentId == null || matchesDepartment(item.department(), item.workOrder(), departmentId))
                .filter(item -> counteragentId == null || counteragentId.equals(counteragentId(item)))
                .filter(item -> matchesAttention(item, attentionMode, reminderWindowHours))
                .filter(item -> dateFrom == null || item.costDate() == null || !item.costDate().isBefore(dateFrom))
                .filter(item -> dateTo == null || item.costDate() == null || item.costDate().isBefore(dateTo))
                .filter(item -> scopedIds.isEmpty() || scopedIds.contains(item.id()))
                .filter(item -> !Boolean.TRUE.equals(myQueue) || matchesCurrentReviewQueue(item))
                .filter(item -> matchesAllocationStatus(item, allocationStatus))
                .toList();
    }

    private boolean matchesAllocationStatus(ActualCostReviewItem item, String allocationStatus) {
        if (allocationStatus == null || allocationStatus.isBlank() || "ALL".equalsIgnoreCase(allocationStatus)) {
            return true;
        }
        if ("UNALLOCATED".equalsIgnoreCase(allocationStatus)) {
            return item.budgetLine() == null || item.unallocated();
        }
        if ("ALLOCATED".equalsIgnoreCase(allocationStatus)) {
            return item.budgetLine() != null && !item.unallocated();
        }
        throw RestException.badRequest("Unsupported allocationStatus: " + allocationStatus);
    }

    private List<ActualCostReviewActivityItem> filterActivityItems(List<ActualCostReviewActivityItem> items,
                                                                   String eventGroup,
                                                                   UUID departmentId,
                                                                   String roleCode,
                                                                   UUID actualCostId,
                                                                   String actualCostIds) {
        Set<UUID> scopedIds = parseActualCostIds(actualCostId, actualCostIds);
        return items.stream()
                .filter(item -> eventGroup == null || eventGroup.isBlank() || eventGroup.equalsIgnoreCase(item.eventGroup()))
                .filter(item -> departmentId == null || matchesDepartment(item.department(), item.workOrder(), departmentId))
                .filter(item -> roleCode == null || roleCode.isBlank()
                        || roleCode.equalsIgnoreCase(item.recipientRoleCode())
                        || roleCode.equalsIgnoreCase(item.approvalRoleCode()))
                .filter(item -> scopedIds.isEmpty() || scopedIds.contains(item.actualCostId()))
                .toList();
    }

    private List<ActualCostReviewHandoverItem> filterHandoverItems(List<ActualCostReviewHandoverItem> items,
                                                                  UUID departmentId,
                                                                  String approvalRoleCode,
                                                                  UUID actualCostId,
                                                                  String actualCostIds) {
        Set<UUID> scopedIds = parseActualCostIds(actualCostId, actualCostIds);
        return items.stream()
                .filter(item -> departmentId == null || matchesDepartment(item.department(), item.workOrder(), departmentId))
                .filter(item -> approvalRoleCode == null || approvalRoleCode.isBlank()
                        || approvalRoleCode.equalsIgnoreCase(item.nextApprovalRoleCode())
                        || approvalRoleCode.equalsIgnoreCase(item.previousApprovalRoleCode()))
                .filter(item -> scopedIds.isEmpty() || scopedIds.contains(item.actualCostId()))
                .toList();
    }

    private boolean matchesAttention(ActualCostReviewItem item, String attentionMode, Integer reminderWindowHours) {
        if (attentionMode == null || attentionMode.isBlank() || "ALL".equalsIgnoreCase(attentionMode)) {
            return true;
        }
        if ("OVERDUE".equalsIgnoreCase(attentionMode)) {
            return item.isOverdue();
        }
        if ("DUE_SOON".equalsIgnoreCase(attentionMode)) {
            int reminderWindow = reminderWindowHours != null ? reminderWindowHours : 4;
            return !item.isOverdue() && item.hoursToOverdue() <= reminderWindow;
        }
        return true;
    }

    private boolean matchesDepartment(Object department, Object workOrder, UUID departmentId) {
        UUID directDepartmentId = objectId(department);
        if (departmentId.equals(directDepartmentId)) {
            return true;
        }
        Object workOrderDepartment = objectProperty(workOrder, "department");
        return departmentId.equals(objectId(workOrderDepartment));
    }

    private UUID counteragentId(ActualCostReviewItem item) {
        Object counteragentWork = item.counteragentWork();
        Object counteragent = objectProperty(counteragentWork, "counteragent");
        return objectId(counteragent);
    }

    private UUID objectId(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        Object nestedId = objectProperty(value, "id");
        if (nestedId == value) {
            return null;
        }
        return objectId(nestedId);
    }

    private Object objectProperty(Object value, String name) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return map.get(name);
        }
        try {
            return value.getClass().getMethod(name).invoke(value);
        } catch (ReflectiveOperationException | SecurityException ex) {
            return null;
        }
    }

    private Set<UUID> parseActualCostIds(UUID actualCostId, String actualCostIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (actualCostId != null) {
            ids.add(actualCostId);
        }
        if (actualCostIds != null && !actualCostIds.isBlank()) {
            for (String rawId : actualCostIds.split(",")) {
                if (!rawId.isBlank()) {
                    ids.add(UUID.fromString(rawId.trim()));
                }
            }
        }
        return ids;
    }

    private Instant parseDateStart(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant parseDateEnd(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private boolean matchesCurrentReviewQueue(ActualCostReviewItem item) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();
        if (authentication == null) {
            return false;
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (authorities.contains("SYSTEM_ADMIN") || authorities.contains("*")) {
            return true;
        }
        return authorities.contains(item.effectiveReviewRoleCode())
                || authorities.contains(item.approvalRoleCode())
                || authorities.contains("ACTUAL_COST_APPROVE");
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

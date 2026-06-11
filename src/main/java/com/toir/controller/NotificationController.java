package com.toir.controller;
import com.toir.dto.common.PageResponseWithSummary;
import com.toir.dto.notification.FinancialReviewInboxSummary;
import com.toir.dto.notification.NotificationDispatchResponse;
import com.toir.dto.notification.NotificationDto;
import com.toir.dto.notification.NotificationEvaluationResponse;
import com.toir.dto.notification.NotificationSummaryDto;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.dto.sla.SlaRuleDto;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.PermissionConstants;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.NotificationFacadeService;
import com.toir.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final NotificationFacadeService notificationFacadeService;

    private static final String READ_AUTH = "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('NOTIFICATION_READ')";
    private static final String MARK_READ_AUTH = "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('NOTIFICATION_MARK_READ')";
    private static final String ADMIN_AUTH = "hasAuthority('SYSTEM_ADMIN') or hasAuthority('*') or hasAuthority('NOTIFICATION_ADMIN')";


    @GetMapping
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Page<NotificationDto>> list(@RequestParam(required = false) UUID recipientId,
                                      @RequestParam(required = false) String search,
                                      @RequestParam(required = false) NotificationStatus status,
                                      @RequestParam(required = false) NotificationSeverity severity,
                                      @RequestParam(required = false) String entityType,
                                      @RequestParam(required = false) Boolean unreadOnly,
                                      @CurrentUser AuthenticatedUser user, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        UUID target = resolveRecipient(recipientId, user);
        return ResponseEntity.ok(notificationFacadeService.list(target, page, size, search, status, severity, entityType, Boolean.TRUE.equals(unreadOnly)));
    }

    @GetMapping("/summary")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<NotificationSummaryDto> summary(@CurrentUser AuthenticatedUser user) {
        UUID target = user != null ? UUID.fromString(user.id()) : null;
        return ResponseEntity.ok(notificationFacadeService.summary(target));
    }

    @GetMapping("/unread-count")
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<Long> unreadCount(@RequestParam(required = false) UUID recipientId,
                            @CurrentUser AuthenticatedUser user) {
        UUID target = resolveRecipient(recipientId, user);
        return ResponseEntity.ok(notificationFacadeService.unreadCount(target));
    }

    @PostMapping
    @PreAuthorize(ADMIN_AUTH)
    public ResponseEntity<NotificationDto> send(@Valid @RequestBody NotificationDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(r));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize(MARK_READ_AUTH)
    public ResponseEntity<NotificationDto> markRead(@PathVariable UUID id, @CurrentUser AuthenticatedUser user) {
        UUID currentUserId = user != null ? UUID.fromString(user.id()) : null;
        return ResponseEntity.ok(service.markRead(id, currentUserId, isNotificationScopeAdmin(user)));
    }

    @GetMapping("/financial-review-inbox")
    @RequiresSensitiveAccess
    @PreAuthorize(READ_AUTH)
    public ResponseEntity<PageResponseWithSummary<NotificationDto, FinancialReviewInboxSummary>> financialReviewInbox(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        UUID target = user != null ? UUID.fromString(user.id()) : null;
        return ResponseEntity.ok(notificationFacadeService.financialReviewInbox(target, page, size, search));
    }

    @GetMapping("/sla-rules")
    @PreAuthorize(ADMIN_AUTH)
    public ResponseEntity<Page<SlaRuleDto>> slaRules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(notificationFacadeService.slaRules(page, size));
    }

    @PostMapping("/evaluate")
    @PreAuthorize(ADMIN_AUTH)
    public ResponseEntity<NotificationEvaluationResponse> evaluate() {
        return ResponseEntity.ok(notificationFacadeService.evaluate());
    }

    @PostMapping("/dispatch-pending")
    @PreAuthorize(ADMIN_AUTH)
    public ResponseEntity<NotificationDispatchResponse> dispatch() {
        return ResponseEntity.ok(notificationFacadeService.dispatch());
    }

    private UUID resolveRecipient(UUID requestedRecipientId, AuthenticatedUser user) {
        if (user == null) {
            return requestedRecipientId;
        }
        UUID currentUserId = UUID.fromString(user.id());
        if (requestedRecipientId == null || requestedRecipientId.equals(currentUserId) || isNotificationScopeAdmin(user)) {
            return requestedRecipientId != null ? requestedRecipientId : currentUserId;
        }
        throw new AccessDeniedException("Access denied by notification recipient scope");
    }

    private boolean isNotificationScopeAdmin(AuthenticatedUser user) {
        if (user == null) {
            return false;
        }
        if ("SYSTEM_ADMIN".equals(user.primaryRoleCode())) {
            return true;
        }
        if (user.permissions() != null
                && (user.permissions().contains(PermissionConstants.WILDCARD)
                || user.permissions().contains(PermissionConstants.NOTIFICATION_ADMIN))) {
            return true;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> PermissionConstants.WILDCARD.equals(authority.getAuthority())
                        || PermissionConstants.NOTIFICATION_ADMIN.equals(authority.getAuthority()));
    }
}

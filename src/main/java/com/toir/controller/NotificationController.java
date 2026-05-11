package com.toir.controller;
import com.toir.dto.common.PageResponseWithSummary;
import com.toir.dto.notification.FinancialReviewInboxSummary;
import com.toir.dto.notification.NotificationDispatchResponse;
import com.toir.dto.notification.NotificationDto;
import com.toir.dto.notification.NotificationEvaluationResponse;
import com.toir.dto.notification.NotificationSummaryDto;
import com.toir.dto.sla.SlaRuleDto;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.security.RequiresSensitiveAccess;
import com.toir.service.NotificationFacadeService;
import com.toir.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final NotificationFacadeService notificationFacadeService;


    @GetMapping
    public ResponseEntity<Page<NotificationDto>> list(@RequestParam(required = false) UUID recipientId,
                                      @CurrentUser AuthenticatedUser user, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        UUID target = recipientId != null ? recipientId
                : (user != null ? UUID.fromString(user.id()) : null);
        return ResponseEntity.ok(notificationFacadeService.list(target, page, size));
    }

    @GetMapping("/summary")
    public ResponseEntity<NotificationSummaryDto> summary(@CurrentUser AuthenticatedUser user) {
        UUID target = user != null ? UUID.fromString(user.id()) : null;
        return ResponseEntity.ok(notificationFacadeService.summary(target));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount(@RequestParam(required = false) UUID recipientId,
                            @CurrentUser AuthenticatedUser user) {
        UUID target = recipientId != null ? recipientId
                : (user != null ? UUID.fromString(user.id()) : null);
        return ResponseEntity.ok(notificationFacadeService.unreadCount(target));
    }

    @PostMapping
    public ResponseEntity<NotificationDto> send(@Valid @RequestBody NotificationDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(r));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markRead(@PathVariable UUID id) { return ResponseEntity.ok(service.markRead(id)); }

    @GetMapping("/financial-review-inbox")
    @RequiresSensitiveAccess
    public ResponseEntity<PageResponseWithSummary<NotificationDto, FinancialReviewInboxSummary>> financialReviewInbox(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        UUID target = user != null ? UUID.fromString(user.id()) : null;
        return ResponseEntity.ok(notificationFacadeService.financialReviewInbox(target, page, size, search));
    }

    @GetMapping("/sla-rules")
    public ResponseEntity<Page<SlaRuleDto>> slaRules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(notificationFacadeService.slaRules(page, size));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<NotificationEvaluationResponse> evaluate() {
        return ResponseEntity.ok(notificationFacadeService.evaluate());
    }

    @PostMapping("/dispatch-pending")
    public ResponseEntity<NotificationDispatchResponse> dispatch() {
        return ResponseEntity.ok(notificationFacadeService.dispatch());
    }
}

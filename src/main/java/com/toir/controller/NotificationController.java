package com.toir.controller;
import com.toir.dto.common.PageResponseWithSummary;
import com.toir.dto.notification.FinancialReviewInboxSummary;
import com.toir.dto.notification.NotificationDispatchResponse;
import com.toir.dto.notification.NotificationDto;
import com.toir.dto.notification.NotificationEvaluationResponse;
import com.toir.dto.notification.NotificationSummaryDto;
import com.toir.dto.sla.SlaRuleDto;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.NotificationService;
import com.toir.service.SlaRuleService;
import com.toir.util.PaginationUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "notifications")
public class NotificationController {

    private final NotificationService service;
    private final SlaRuleService slaRuleService;

    public NotificationController(NotificationService service, SlaRuleService slaRuleService) {
        this.service = service;
        this.slaRuleService = slaRuleService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> list(@RequestParam(required = false) UUID recipientId,
                                      @CurrentUser AuthenticatedUser user) {
        UUID target = recipientId != null ? recipientId
                : (user != null ? UUID.fromString(user.id()) : null);
        return ResponseEntity.ok(target != null ? service.findForUser(target) : List.of());
    }

    @GetMapping("/summary")
    public ResponseEntity<NotificationSummaryDto> summary(@CurrentUser AuthenticatedUser user) {
        long unread = (user != null) ? service.countUnread(UUID.fromString(user.id())) : 0;
        return ResponseEntity.ok(new NotificationSummaryDto(unread, 0, 0, 0, 0, 0));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount(@RequestParam(required = false) UUID recipientId,
                            @CurrentUser AuthenticatedUser user) {
        UUID target = recipientId != null ? recipientId
                : (user != null ? UUID.fromString(user.id()) : null);
        return ResponseEntity.ok(target != null ? service.countUnread(target) : 0);
    }

    @PostMapping
    public ResponseEntity<NotificationDto> send(@Valid @RequestBody NotificationDto r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(r));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markRead(@PathVariable UUID id) { return ResponseEntity.ok(service.markRead(id)); }

    @GetMapping("/financial-review-inbox")
    public ResponseEntity<PageResponseWithSummary<NotificationDto, FinancialReviewInboxSummary>> financialReviewInbox(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<NotificationDto> items = user != null
                ? service.findForUser(UUID.fromString(user.id())).stream()
                        .filter(n -> n.entityType() != null && n.entityType().toUpperCase().contains("COST"))
                        .toList()
                : List.of();
        long read = items.stream().filter(n -> n.status() == NotificationStatus.READ).count();
        long dueSoon = items.stream().filter(n -> n.severity() == NotificationSeverity.WARNING).count();
        long overdue = items.stream().filter(n -> n.severity() == NotificationSeverity.CRITICAL).count();
        return ResponseEntity.ok(PageResponseWithSummary.of(
                items,
                page,
                pageSize,
                new FinancialReviewInboxSummary(
                        items.size(),
                        items.size() - read,
                        dueSoon,
                        overdue,
                        read,
                        items.size() - read
                )
        ));
    }

    @GetMapping("/sla-rules")
    public ResponseEntity<Page<SlaRuleDto>> slaRules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        var pageable = PaginationUtils.pageRequest(page, pageSize);
        List<SlaRuleDto> all = slaRuleService.findAll();
        int fromIndex = Math.min(PaginationUtils.offset(pageable), all.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), all.size());
        List<SlaRuleDto> items = all.subList(fromIndex, toIndex);
        return ResponseEntity.ok(PaginationUtils.page(items, pageable.getPageNumber(), pageable.getPageSize(), all.size()));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<NotificationEvaluationResponse> evaluate() {
        return ResponseEntity.ok(new NotificationEvaluationResponse(0, 0, 0));
    }

    @PostMapping("/dispatch-pending")
    public ResponseEntity<NotificationDispatchResponse> dispatch() {
        return ResponseEntity.ok(new NotificationDispatchResponse(0));
    }
}

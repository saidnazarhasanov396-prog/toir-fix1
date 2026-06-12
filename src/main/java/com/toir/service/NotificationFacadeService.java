package com.toir.service;

import com.toir.dto.common.PageResponseWithSummary;
import com.toir.dto.notification.FinancialReviewInboxSummary;
import com.toir.dto.notification.NotificationDispatchResponse;
import com.toir.dto.notification.NotificationDto;
import com.toir.dto.notification.NotificationEvaluationResponse;
import com.toir.dto.notification.NotificationSummaryDto;
import com.toir.dto.sla.SlaRuleDto;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationFacadeService {

    private final NotificationService notificationService;
    private final SlaRuleService slaRuleService;

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(UUID recipientId, int page, int size) {
        return list(recipientId, page, size, null, null, null, null, false);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(UUID recipientId, int page, int size, String search,
                                      NotificationStatus status, NotificationSeverity severity, String entityType,
                                      boolean unreadOnly) {
        List<NotificationDto> items = recipientId != null ? notificationService.findForUser(recipientId) : List.of();
        items = items.stream()
                .filter(n -> !unreadOnly || n.status() != NotificationStatus.READ)
                .filter(n -> status == null || n.status() == status)
                .filter(n -> severity == null || n.severity() == severity)
                .filter(n -> entityType == null || entityType.isBlank() || entityType.equalsIgnoreCase(n.entityType()))
                .filter(n -> search == null || search.isBlank()
                        || containsIgnoreCase(n.title(), search)
                        || containsIgnoreCase(n.message(), search)
                        || containsIgnoreCase(n.entityType(), search)
                        || containsIgnoreCase(n.entityId(), search))
                .toList();
        return PaginationUtils.page(items, page, size);
    }

    @Transactional(readOnly = true)
    public NotificationSummaryDto summary(UUID recipientId) {
        long unread = recipientId != null ? notificationService.countUnread(recipientId) : 0;
        return new NotificationSummaryDto(unread, 0, 0, 0, 0, 0);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return recipientId != null ? notificationService.countUnread(recipientId) : 0;
    }

    @Transactional(readOnly = true)
    public PageResponseWithSummary<NotificationDto, FinancialReviewInboxSummary> financialReviewInbox(
            UUID recipientId, int page, int size, String search
    ) {
        List<NotificationDto> items = recipientId != null
                ? notificationService.findForUser(recipientId).stream()
                .filter(n -> n.entityType() != null && n.entityType().toUpperCase().contains("COST"))
                .filter(n -> search == null || search.isBlank()
                        || containsIgnoreCase(n.title(), search)
                        || containsIgnoreCase(n.message(), search)
                        || containsIgnoreCase(n.entityType(), search))
                .toList()
                : List.of();

        long read = items.stream().filter(n -> n.status() == NotificationStatus.READ).count();
        long dueSoon = items.stream().filter(n -> n.severity() == NotificationSeverity.WARNING).count();
        long overdue = items.stream().filter(n -> n.severity() == NotificationSeverity.CRITICAL).count();

        return PageResponseWithSummary.of(
                items,
                page,
                size,
                new FinancialReviewInboxSummary(
                        items.size(),
                        items.size() - read,
                        dueSoon,
                        overdue,
                        read,
                        items.size() - read
                )
        );
    }

    @Transactional(readOnly = true)
    public Page<SlaRuleDto> slaRules(int page, int size) {
        var pageable = PaginationUtils.pageRequest(page, size);
        List<SlaRuleDto> all = slaRuleService.findAll();
        int fromIndex = Math.min(PaginationUtils.offset(pageable), all.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), all.size());
        List<SlaRuleDto> items = all.subList(fromIndex, toIndex);
        return PaginationUtils.page(items, pageable.getPageNumber(), pageable.getPageSize(), all.size());
    }

    @Transactional(readOnly = true)
    public NotificationEvaluationResponse evaluate() {
        return new NotificationEvaluationResponse(0, 0, 0);
    }

    @Transactional(readOnly = true)
    public NotificationDispatchResponse dispatch() {
        return new NotificationDispatchResponse(0);
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase().contains(search.toLowerCase());
    }
}

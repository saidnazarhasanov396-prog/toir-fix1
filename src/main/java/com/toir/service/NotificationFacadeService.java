package com.toir.service;

import com.toir.dto.common.PageResponseWithSummary;
import com.toir.dto.notification.BulkFinancialReviewInboxAcknowledgementResponse;
import com.toir.dto.notification.BulkNotificationReadResponse;
import com.toir.dto.notification.FinancialReviewInboxAcknowledgementResponse;
import com.toir.dto.notification.FinancialReviewInboxFilter;
import com.toir.dto.notification.FinancialReviewInboxItem;
import com.toir.dto.notification.FinancialReviewInboxSummary;
import com.toir.dto.notification.NotificationDispatchResponse;
import com.toir.dto.notification.NotificationDto;
import com.toir.dto.notification.NotificationEvaluationResponse;
import com.toir.dto.notification.NotificationSummaryDto;
import com.toir.dto.sla.SlaRuleDto;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.util.CsvWriter;
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
    public PageResponseWithSummary<FinancialReviewInboxItem, FinancialReviewInboxSummary> financialReviewInbox(
            UUID recipientId, int page, int size, String search
    ) {
        return financialReviewInbox(recipientId, page, size, new FinancialReviewInboxFilter(search, null, null, null, null, null));
    }

    @Transactional(readOnly = true)
    public PageResponseWithSummary<FinancialReviewInboxItem, FinancialReviewInboxSummary> financialReviewInbox(
            UUID recipientId, int page, int size, FinancialReviewInboxFilter filter
    ) {
        FinancialReviewInboxFilter safeFilter = filter != null
                ? filter
                : new FinancialReviewInboxFilter(null, null, null, null, null, null);
        List<NotificationDto> notifications = recipientId != null
                ? notificationService.findForUser(recipientId).stream()
                .filter(n -> n.entityType() != null && n.entityType().toUpperCase().contains("COST"))
                .filter(n -> matchesFinancialReviewInboxFilter(n, safeFilter))
                .toList()
                : List.of();

        List<FinancialReviewInboxItem> items = notifications.stream().map(this::toFinancialReviewInboxItem).toList();

        long read = notifications.stream().filter(n -> n.status() == NotificationStatus.READ).count();
        long dueSoon = notifications.stream().filter(n -> n.severity() == NotificationSeverity.WARNING).count();
        long overdue = notifications.stream().filter(n -> n.severity() == NotificationSeverity.CRITICAL).count();
        long acknowledged = notifications.stream().filter(n -> n.acknowledgedAt() != null).count();

        return PageResponseWithSummary.of(
                items,
                page,
                size,
                new FinancialReviewInboxSummary(
                        items.size(),
                        items.size() - read,
                        dueSoon,
                        overdue,
                        acknowledged,
                        items.size() - acknowledged
                )
        );
    }

    @Transactional(readOnly = true)
    public String financialReviewInboxCsv(UUID recipientId, String search) {
        return financialReviewInboxCsv(recipientId, new FinancialReviewInboxFilter(search, null, null, null, null, null));
    }

    @Transactional(readOnly = true)
    public String financialReviewInboxCsv(UUID recipientId, FinancialReviewInboxFilter filter) {
        List<FinancialReviewInboxItem> items = financialReviewInbox(recipientId, 0, Integer.MAX_VALUE, filter).content();
        return CsvWriter.build(
                List.of("id", "title", "severity", "status", "entityType", "entityId", "acknowledgedAt"),
                items,
                List.of(
                        FinancialReviewInboxItem::id,
                        FinancialReviewInboxItem::title,
                        FinancialReviewInboxItem::severity,
                        FinancialReviewInboxItem::status,
                        FinancialReviewInboxItem::entityType,
                        FinancialReviewInboxItem::entityId,
                        FinancialReviewInboxItem::acknowledgedAt
                )
        );
    }

    @Transactional
    public BulkNotificationReadResponse bulkMarkFinancialReviewInboxRead(
            List<UUID> ids,
            UUID currentUserId,
            boolean scopeAdmin
    ) {
        return notificationService.bulkMarkRead(ids, currentUserId, scopeAdmin);
    }

    @Transactional
    public FinancialReviewInboxAcknowledgementResponse acknowledgeFinancialReviewInbox(
            UUID id,
            UUID currentUserId,
            boolean scopeAdmin,
            String comment
    ) {
        return notificationService.acknowledge(id, currentUserId, scopeAdmin, comment);
    }

    @Transactional
    public BulkFinancialReviewInboxAcknowledgementResponse bulkAcknowledgeFinancialReviewInbox(
            List<UUID> ids,
            UUID currentUserId,
            boolean scopeAdmin,
            String comment
    ) {
        return notificationService.bulkAcknowledge(ids, currentUserId, scopeAdmin, comment);
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

    private boolean matchesFinancialReviewInboxFilter(NotificationDto notification, FinancialReviewInboxFilter filter) {
        if (hasText(filter.search())
                && !containsIgnoreCase(notification.title(), filter.search())
                && !containsIgnoreCase(notification.message(), filter.search())
                && !containsIgnoreCase(notification.entityType(), filter.search())
                && !containsIgnoreCase(notification.entityId(), filter.search())) {
            return false;
        }
        if (hasText(filter.kind()) && !filter.kind().equalsIgnoreCase(kind(notification))) {
            return false;
        }
        if (Boolean.TRUE.equals(filter.unreadOnly()) && notification.status() == NotificationStatus.READ) {
            return false;
        }
        if ("ACKNOWLEDGED".equalsIgnoreCase(filter.acknowledgementMode()) && notification.acknowledgedAt() == null) {
            return false;
        }
        if ("UNACKNOWLEDGED".equalsIgnoreCase(filter.acknowledgementMode()) && notification.acknowledgedAt() != null) {
            return false;
        }
        // Notification rows do not currently persist financial review department/role metadata.
        // Keep these params accepted at the API boundary without hiding records that cannot be enriched yet.
        return true;
    }

    private FinancialReviewInboxItem toFinancialReviewInboxItem(NotificationDto notification) {
        return new FinancialReviewInboxItem(
                notification.id(),
                notification.recipientId(),
                notification.title(),
                notification.message(),
                notification.channel(),
                notification.status(),
                notification.severity(),
                notification.entityType(),
                notification.entityId(),
                notification.readAt(),
                notification.createdAt(),
                kind(notification),
                null,
                null,
                null,
                null,
                null,
                actionPath(notification),
                notification.acknowledgedAt() != null,
                notification.acknowledgedAt(),
                notification.acknowledgedById() != null
                        ? new FinancialReviewInboxItem.UserRef(notification.acknowledgedById(), "", "")
                        : null,
                notification.acknowledgementComment()
        );
    }

    private String actionPath(NotificationDto notification) {
        if (notification.entityId() == null || notification.entityId().isBlank()) {
            return "/financial-review";
        }
        return "/financial-review?actualCostId=" + notification.entityId();
    }

    private String kind(NotificationDto notification) {
        return notification.severity() == NotificationSeverity.CRITICAL ? "OVERDUE" : "DUE_SOON";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

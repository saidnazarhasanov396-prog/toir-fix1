package com.toir.service;

import com.toir.dto.common.PageResponseWithSummary;
import com.toir.dto.financialreview.ActualCostReviewItem;
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
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.security.PermissionConstants;
import com.toir.security.ScopeAccessService;
import com.toir.util.CsvWriter;
import com.toir.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import com.toir.util.SortUtils;

@Service
@RequiredArgsConstructor
public class NotificationFacadeService {

    private final NotificationService notificationService;
    private final SlaRuleService slaRuleService;
    private final ScopeAccessService scopeAccessService;
    private final ActualCostReviewFacadeService actualCostReviewFacadeService;

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(UUID recipientId, int page, int size) {
        return list(recipientId, page, size, null, null, null, null, false);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(UUID recipientId, int page, int size, String search,
                                      NotificationStatus status, NotificationSeverity severity, String entityType,
                                      boolean unreadOnly) {
        return list(recipientId, page, size, search, status, severity, entityType, unreadOnly, null, null);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(UUID recipientId, int page, int size, String search,
                                      NotificationStatus status, NotificationSeverity severity, String entityType,
                                      boolean unreadOnly, String sortBy, String sortDir) {
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
        Comparator<NotificationDto> comparator = switch (sortBy == null ? "" : sortBy.trim()) {
            case "type" -> Comparator.comparing(
                    NotificationDto::entityType,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "severity" -> Comparator.comparing(
                    NotificationDto::severity,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "read" -> Comparator.comparing(n -> n.status() == NotificationStatus.READ);
            case "createdAt" -> Comparator.comparing(
                    NotificationDto::createdAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
        if (comparator != null) {
            if (SortUtils.direction(sortDir, Sort.Direction.DESC).isDescending()) {
                comparator = comparator.reversed();
            }
            items = items.stream().sorted(comparator).toList();
        }
        return PaginationUtils.page(items, page, size);
    }

    @Transactional(readOnly = true)
    public NotificationSummaryDto summary(UUID recipientId) {
        long unread = recipientId != null ? notificationService.countUnread(recipientId) : 0;

        List<NotificationDto> personal = recipientId != null
                ? notificationService.findForUser(recipientId)
                : List.of();
        long critical = personal.stream()
                .filter(n -> n.status() != NotificationStatus.READ)
                .filter(n -> n.severity() == NotificationSeverity.CRITICAL)
                .count();
        long openEscalations = personal.stream()
                .filter(n -> n.status() != NotificationStatus.READ)
                .filter(n -> n.severity() == NotificationSeverity.CRITICAL
                        || n.severity() == NotificationSeverity.WARNING)
                .count();

        // Badge must match Review Queue: only PENDING actual costs in finance scope.
        List<ActualCostReviewItem> pendingQueue = pendingReviewQueueItems();
        long overdue = pendingQueue.stream().filter(ActualCostReviewItem::isOverdue).count();
        long dueSoon = pendingQueue.stream().filter(item -> !item.isOverdue()).count();

        return new NotificationSummaryDto(
                unread,
                critical,
                openEscalations,
                pendingQueue.size(),
                dueSoon,
                overdue
        );
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

        // Only PENDING actual costs (same source as Review Queue). Drop stale COST notifications.
        List<ActualCostReviewItem> pendingQueue = pendingReviewQueueItems();
        java.util.Map<String, ActualCostReviewItem> pendingById = pendingQueue.stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.id().toString(),
                        item -> item,
                        (a, b) -> a
                ));

        List<NotificationDto> activeNotifications = financialReviewNotifications(recipientId).stream()
                .filter(notification -> hasText(notification.entityId())
                        && pendingById.containsKey(notification.entityId().trim()))
                .toList();
        Set<String> notifiedIds = notifiedEntityIds(activeNotifications);

        List<FinancialReviewInboxItem> items = Stream.concat(
                activeNotifications.stream().map(notification -> toFinancialReviewInboxItem(
                        notification,
                        pendingById.get(notification.entityId().trim())
                )),
                pendingQueue.stream()
                        .filter(item -> !notifiedIds.contains(item.id().toString()))
                        .map(this::toSyntheticInboxItem)
        )
                .filter(item -> matchesInboxItemFilter(item, safeFilter))
                .toList();

        long read = items.stream().filter(item -> item.status() == NotificationStatus.READ).count();
        long dueSoon = items.stream().filter(item -> "DUE_SOON".equalsIgnoreCase(item.kind())).count();
        long overdue = items.stream().filter(item -> "OVERDUE".equalsIgnoreCase(item.kind())).count();
        long acknowledged = items.stream().filter(FinancialReviewInboxItem::isAcknowledged).count();

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

    private List<NotificationDto> financialReviewNotifications(UUID recipientId) {
        if (scopeAccessService.isScopeAdmin()) {
            return notificationService.findAllFinancialReviewInbox();
        }
        if (recipientId == null) {
            return List.of();
        }
        return notificationService.findForUser(recipientId).stream()
                .filter(this::isFinancialReviewNotification)
                .toList();
    }

    private boolean isFinancialReviewNotification(NotificationDto notification) {
        return notification.entityType() != null
                && notification.entityType().toUpperCase().contains("COST");
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase().contains(search.toLowerCase());
    }

    private Set<String> notifiedEntityIds(List<NotificationDto> notifications) {
        Set<String> ids = new HashSet<>();
        for (NotificationDto notification : notifications) {
            if (hasText(notification.entityId())) {
                ids.add(notification.entityId().trim());
            }
        }
        return ids;
    }

    /**
     * Pending finance review items visible to the current user (department scope already applied).
     * Approver/reject roles and scope admins see the full scoped queue; others only role-matched items.
     */
    private List<ActualCostReviewItem> pendingReviewQueueItems() {
        return actualCostReviewFacadeService.reviewQueue(null).stream()
                .filter(this::matchesReviewQueueItem)
                .toList();
    }

    private boolean matchesReviewQueueItem(ActualCostReviewItem item) {
        if (scopeAccessService.isScopeAdmin()) {
            return true;
        }
        if (scopeAccessService.hasAuthority(PermissionConstants.ACTUAL_COST_APPROVE)
                || scopeAccessService.hasAuthority(PermissionConstants.ACTUAL_COST_REJECT)) {
            return true;
        }
        if (hasText(item.effectiveReviewRoleCode())
                && scopeAccessService.hasAuthority(item.effectiveReviewRoleCode())) {
            return true;
        }
        return hasText(item.approvalRoleCode())
                && scopeAccessService.hasAuthority(item.approvalRoleCode());
    }

    private FinancialReviewInboxItem toSyntheticInboxItem(ActualCostReviewItem item) {
        NotificationSeverity severity = item.isOverdue()
                ? NotificationSeverity.CRITICAL
                : item.hoursToOverdue() <= 4
                ? NotificationSeverity.WARNING
                : NotificationSeverity.INFO;
        String kind = severity == NotificationSeverity.CRITICAL ? "OVERDUE" : "DUE_SOON";
        String actionPath = hasText(item.reviewActionPath())
                ? item.reviewActionPath()
                : "/financial-review?actualCostId=" + item.id();
        return new FinancialReviewInboxItem(
                item.id(),
                null,
                "Actual cost pending review",
                "Actual cost " + item.id() + " requires finance review.",
                NotificationChannel.WEB,
                NotificationStatus.SENT,
                severity,
                "ACTUAL_COST",
                item.id().toString(),
                null,
                item.costDate(),
                kind,
                item.approvalRoleCode(),
                item.escalationRoleCode(),
                null,
                4,
                item.hoursToOverdue(),
                actionPath,
                false,
                null,
                null,
                null
        );
    }

    private boolean matchesInboxItemFilter(FinancialReviewInboxItem item, FinancialReviewInboxFilter filter) {
        if (hasText(filter.search())
                && !containsIgnoreCase(item.title(), filter.search())
                && !containsIgnoreCase(item.message(), filter.search())
                && !containsIgnoreCase(item.entityType(), filter.search())
                && !containsIgnoreCase(item.entityId(), filter.search())) {
            return false;
        }
        if (hasText(filter.kind()) && !filter.kind().equalsIgnoreCase(item.kind())) {
            return false;
        }
        if (Boolean.TRUE.equals(filter.unreadOnly()) && item.status() == NotificationStatus.READ) {
            return false;
        }
        if ("ACKNOWLEDGED".equalsIgnoreCase(filter.acknowledgementMode()) && !item.isAcknowledged()) {
            return false;
        }
        if ("UNACKNOWLEDGED".equalsIgnoreCase(filter.acknowledgementMode()) && item.isAcknowledged()) {
            return false;
        }
        return true;
    }

    private FinancialReviewInboxItem toFinancialReviewInboxItem(NotificationDto notification,
                                                                ActualCostReviewItem pending) {
        String kind = pending != null
                ? (pending.isOverdue() ? "OVERDUE" : "DUE_SOON")
                : kind(notification);
        NotificationSeverity severity = pending != null && pending.isOverdue()
                ? NotificationSeverity.CRITICAL
                : notification.severity();
        return new FinancialReviewInboxItem(
                notification.id(),
                notification.recipientId(),
                notification.title(),
                notification.message(),
                notification.channel(),
                notification.status(),
                severity,
                notification.entityType(),
                notification.entityId(),
                notification.readAt(),
                notification.createdAt(),
                kind,
                pending != null ? pending.approvalRoleCode() : null,
                pending != null ? pending.escalationRoleCode() : null,
                null,
                pending != null ? 4 : null,
                pending != null ? pending.hoursToOverdue() : null,
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

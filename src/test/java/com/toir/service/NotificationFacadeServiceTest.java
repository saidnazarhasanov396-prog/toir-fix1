package com.toir.service;

import com.toir.dto.financialreview.ActualCostReviewItem;
import com.toir.dto.notification.NotificationDto;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.security.ScopeAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationFacadeServiceTest {

    @Mock
    NotificationService notificationService;

    @Mock
    SlaRuleService slaRuleService;

    @Mock
    ScopeAccessService scopeAccessService;

    @Mock
    ActualCostReviewFacadeService actualCostReviewFacadeService;

    @InjectMocks
    NotificationFacadeService service;

    @Test
    void listAppliesSupportedFiltersBeforePagination() {
        UUID recipientId = UUID.randomUUID();
        NotificationDto matching = notification(
                recipientId,
                "Pump repair",
                "WO-1 pump needs repair",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "WorkOrder",
                UUID.randomUUID().toString()
        );
        NotificationDto wrongStatus = notification(
                recipientId,
                "Pump repair",
                "WO-2 pump needs repair",
                NotificationStatus.READ,
                NotificationSeverity.WARNING,
                "WorkOrder",
                UUID.randomUUID().toString()
        );
        NotificationDto wrongEntity = notification(
                recipientId,
                "Pump repair",
                "Cost review",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "ActualCost",
                UUID.randomUUID().toString()
        );

        when(notificationService.findForUser(recipientId))
                .thenReturn(List.of(matching, wrongStatus, wrongEntity));

        var result = service.list(
                recipientId,
                0,
                10,
                "pump",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "WorkOrder",
                false
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).containsExactly(matching);
    }

    @Test
    void listCanFilterUnreadNotifications() {
        UUID recipientId = UUID.randomUUID();
        NotificationDto sent = notification(
                recipientId,
                "Sent",
                "Unread notification",
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "WorkOrder",
                UUID.randomUUID().toString()
        );
        NotificationDto read = notification(
                recipientId,
                "Read",
                "Read notification",
                NotificationStatus.READ,
                NotificationSeverity.INFO,
                "WorkOrder",
                UUID.randomUUID().toString()
        );

        when(notificationService.findForUser(recipientId)).thenReturn(List.of(read, sent));

        var result = service.list(recipientId, 0, 10, null, null, null, null, true);

        assertThat(result.getContent()).containsExactly(sent);
    }

    @Test
    void listSortsBySeverity() {
        UUID recipientId = UUID.randomUUID();
        NotificationDto info = notification(
                recipientId,
                "Info",
                "Info notification",
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "WorkOrder",
                UUID.randomUUID().toString()
        );
        NotificationDto warning = notification(
                recipientId,
                "Warning",
                "Warning notification",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "WorkOrder",
                UUID.randomUUID().toString()
        );
        NotificationDto critical = notification(
                recipientId,
                "Critical",
                "Critical notification",
                NotificationStatus.SENT,
                NotificationSeverity.CRITICAL,
                "WorkOrder",
                UUID.randomUUID().toString()
        );

        when(notificationService.findForUser(recipientId)).thenReturn(List.of(info, critical, warning));

        var result = service.list(recipientId, 0, 10, null, null, null, null, false, "severity", "desc");

        assertThat(result.getContent()).containsExactly(critical, warning, info);
    }

    @Test
    void summaryCountsOnlyPendingReviewQueueNotStaleCostNotifications() {
        UUID recipientId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        UUID overdueId = UUID.randomUUID();
        NotificationDto criticalUnread = notification(
                recipientId,
                "Critical alert",
                "Critical message",
                NotificationStatus.SENT,
                NotificationSeverity.CRITICAL,
                "WorkOrder",
                UUID.randomUUID().toString()
        );
        NotificationDto warningUnread = notification(
                recipientId,
                "Warning alert",
                "Warning message",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "WorkOrder",
                UUID.randomUUID().toString()
        );
        // Stale COST notifications for already-approved costs must not inflate the badge.
        NotificationDto staleCostNotification = notification(
                recipientId,
                "Old cost review",
                "Already approved",
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "ActualCost",
                UUID.randomUUID().toString()
        );

        when(notificationService.countUnread(recipientId)).thenReturn(3L);
        when(notificationService.findForUser(recipientId))
                .thenReturn(List.of(criticalUnread, warningUnread, staleCostNotification));
        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(actualCostReviewFacadeService.reviewQueue(isNull())).thenReturn(List.of(
                reviewItem(pendingId, false, 10),
                reviewItem(overdueId, true, -2)
        ));

        var result = service.summary(recipientId);

        assertThat(result.unread()).isEqualTo(3);
        assertThat(result.critical()).isEqualTo(1);
        assertThat(result.openEscalations()).isEqualTo(2);
        assertThat(result.financialReviewQueue()).isEqualTo(2);
        assertThat(result.financialReviewDueSoon()).isEqualTo(1);
        assertThat(result.financialReviewOverdue()).isEqualTo(1);
    }

    @Test
    void financialReviewInboxIgnoresStaleCostNotificationsNotInPendingQueue() {
        UUID recipientId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        NotificationDto pendingNotification = notification(
                recipientId,
                "Actual cost pending review",
                "Requires finance review",
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "ActualCost",
                pendingId.toString()
        );
        NotificationDto staleNotification = notification(
                recipientId,
                "Old approved cost",
                "Should be hidden",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "ActualCost",
                UUID.randomUUID().toString()
        );
        NotificationDto workOrderNotification = notification(
                recipientId,
                "Work order overdue",
                "WO overdue",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "WORK_ORDER",
                UUID.randomUUID().toString()
        );

        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(scopeAccessService.hasAuthority(any())).thenReturn(true);
        when(notificationService.findForUser(recipientId))
                .thenReturn(List.of(pendingNotification, staleNotification, workOrderNotification));
        when(actualCostReviewFacadeService.reviewQueue(isNull()))
                .thenReturn(List.of(reviewItem(pendingId, false, 8)));

        var result = service.financialReviewInbox(recipientId, 0, 10, (String) null);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement()
                .extracting("entityId")
                .isEqualTo(pendingId.toString());
        assertThat(result.summary().total()).isEqualTo(1);
    }

    @Test
    void financialReviewInboxReturnsPendingNotificationsForScopeAdmin() {
        UUID adminId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        UUID otherPendingId = UUID.randomUUID();
        NotificationDto ownNotification = notification(
                adminId,
                "Own cost review",
                "Own message",
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "ActualCost",
                pendingId.toString()
        );
        NotificationDto otherNotification = notification(
                UUID.randomUUID(),
                "Finance manager cost review",
                "Other message",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "ACTUAL_COST",
                otherPendingId.toString()
        );
        NotificationDto stale = notification(
                adminId,
                "Stale",
                "Approved already",
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "ActualCost",
                UUID.randomUUID().toString()
        );

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(notificationService.findAllFinancialReviewInbox())
                .thenReturn(List.of(otherNotification, ownNotification, stale));
        when(actualCostReviewFacadeService.reviewQueue(isNull())).thenReturn(List.of(
                reviewItem(pendingId, false, 5),
                reviewItem(otherPendingId, true, -1)
        ));

        var result = service.financialReviewInbox(adminId, 0, 10, (String) null);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting("title")
                .containsExactlyInAnyOrder("Finance manager cost review", "Own cost review");
        assertThat(result.summary().total()).isEqualTo(2);
        assertThat(result.summary().overdue()).isEqualTo(1);
    }

    @Test
    void financialReviewInboxFallsBackToReviewQueueWhenNotificationsMissing() {
        UUID adminId = UUID.randomUUID();
        UUID actualCostId = UUID.randomUUID();

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(notificationService.findAllFinancialReviewInbox()).thenReturn(List.of());
        when(actualCostReviewFacadeService.reviewQueue(isNull()))
                .thenReturn(List.of(reviewItem(actualCostId, false, 22)));

        var result = service.financialReviewInbox(adminId, 0, 10, (String) null);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement()
                .extracting("entityId")
                .isEqualTo(actualCostId.toString());
    }

    private ActualCostReviewItem reviewItem(UUID id, boolean overdue, int hoursToOverdue) {
        return new ActualCostReviewItem(
                id,
                null,
                null,
                null,
                null,
                "PENDING",
                1000.0,
                Instant.parse("2026-07-02T05:37:14.935408Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                2,
                overdue,
                "/financial-review/history/" + id,
                null,
                "FINANCE_MANAGER",
                null,
                hoursToOverdue,
                "RULE",
                null,
                true,
                null,
                "FINANCE_MANAGER",
                "GENERAL",
                "/budgets?actualCostId=" + id,
                "/financial-review?actualCostId=" + id,
                "/financial-review?actualCostId=" + id
        );
    }

    private NotificationDto notification(UUID recipientId, String title, String message,
                                         NotificationStatus status, NotificationSeverity severity,
                                         String entityType, String entityId) {
        return new NotificationDto(
                UUID.randomUUID(),
                recipientId,
                title,
                message,
                NotificationChannel.WEB,
                status,
                severity,
                entityType,
                entityId,
                null
        );
    }
}

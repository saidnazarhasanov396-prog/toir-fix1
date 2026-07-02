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

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
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
                "WorkOrder"
        );
        NotificationDto wrongStatus = notification(
                recipientId,
                "Pump repair",
                "WO-2 pump needs repair",
                NotificationStatus.READ,
                NotificationSeverity.WARNING,
                "WorkOrder"
        );
        NotificationDto wrongEntity = notification(
                recipientId,
                "Pump repair",
                "Cost review",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "ActualCost"
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
                "WorkOrder"
        );
        NotificationDto read = notification(
                recipientId,
                "Read",
                "Read notification",
                NotificationStatus.READ,
                NotificationSeverity.INFO,
                "WorkOrder"
        );

        when(notificationService.findForUser(recipientId)).thenReturn(List.of(read, sent));

        var result = service.list(recipientId, 0, 10, null, null, null, null, true);

        assertThat(result.getContent()).containsExactly(sent);
    }

    @Test
    void financialReviewInboxReturnsOwnCostNotificationsForRegularUser() {
        UUID recipientId = UUID.randomUUID();
        NotificationDto costNotification = notification(
                recipientId,
                "Actual cost pending review",
                "Requires finance review",
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "ActualCost"
        );
        NotificationDto workOrderNotification = notification(
                recipientId,
                "Work order overdue",
                "WO overdue",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "WORK_ORDER"
        );

        when(scopeAccessService.isScopeAdmin()).thenReturn(false);
        when(notificationService.findForUser(recipientId))
                .thenReturn(List.of(costNotification, workOrderNotification));

        var result = service.financialReviewInbox(recipientId, 0, 10, (String) null);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement()
                .extracting("title")
                .isEqualTo("Actual cost pending review");
    }

    @Test
    void financialReviewInboxReturnsAllCostNotificationsForScopeAdmin() {
        UUID adminId = UUID.randomUUID();
        UUID otherRecipientId = UUID.randomUUID();
        NotificationDto ownNotification = notification(
                adminId,
                "Own cost review",
                "Own message",
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "ActualCost"
        );
        NotificationDto otherNotification = notification(
                otherRecipientId,
                "Finance manager cost review",
                "Other message",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "ACTUAL_COST"
        );

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(notificationService.findAllFinancialReviewInbox())
                .thenReturn(List.of(otherNotification, ownNotification));

        var result = service.financialReviewInbox(adminId, 0, 10, (String) null);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting("title")
                .containsExactly("Finance manager cost review", "Own cost review");
    }

    @Test
    void financialReviewInboxFallsBackToReviewQueueWhenNotificationsMissing() {
        UUID adminId = UUID.randomUUID();
        UUID actualCostId = UUID.randomUUID();
        ActualCostReviewItem queueItem = new ActualCostReviewItem(
                actualCostId,
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
                false,
                "/financial-review/history/" + actualCostId,
                null,
                "FINANCE_MANAGER",
                null,
                22,
                "RULE",
                null,
                true,
                null,
                "FINANCE_MANAGER",
                "GENERAL",
                "/budgets?actualCostId=" + actualCostId,
                "/financial-review?actualCostId=" + actualCostId,
                "/financial-review?actualCostId=" + actualCostId
        );

        when(scopeAccessService.isScopeAdmin()).thenReturn(true);
        when(notificationService.findAllFinancialReviewInbox()).thenReturn(List.of());
        when(actualCostReviewFacadeService.reviewQueue(any())).thenReturn(List.of(queueItem));

        var result = service.financialReviewInbox(adminId, 0, 10, (String) null);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).singleElement()
                .extracting("entityId")
                .isEqualTo(actualCostId.toString());
    }

    private NotificationDto notification(UUID recipientId, String title, String message,
                                         NotificationStatus status, NotificationSeverity severity,
                                         String entityType) {
        return new NotificationDto(
                UUID.randomUUID(),
                recipientId,
                title,
                message,
                NotificationChannel.WEB,
                status,
                severity,
                entityType,
                UUID.randomUUID().toString(),
                null
        );
    }
}

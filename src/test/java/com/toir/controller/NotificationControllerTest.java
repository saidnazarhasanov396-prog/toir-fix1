package com.toir.controller;

import com.toir.dto.notification.NotificationDto;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.security.AuthenticatedUser;
import com.toir.service.NotificationService;
import com.toir.service.SlaRuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    NotificationService service;

    @Mock
    SlaRuleService slaRuleService;

    @InjectMocks
    NotificationController controller;

    @Test
    void financialReviewInboxReturnsStandardPageWithSummary() {
        UUID userId = UUID.randomUUID();
        when(service.findForUser(userId)).thenReturn(List.of(
                notification(NotificationStatus.SENT, NotificationSeverity.WARNING, "ACTUAL_COST"),
                notification(NotificationStatus.READ, NotificationSeverity.CRITICAL, "COST_REVIEW"),
                notification(NotificationStatus.SENT, NotificationSeverity.INFO, "WORK_ORDER")
        ));

        Map<String, Object> response = controller.financialReviewInbox(
                new AuthenticatedUser(userId.toString(), "admin", "admin@test.local", "Admin", null, "ADMIN", List.of()),
                1,
                1
        );

        assertThat(response).containsKeys(
                "content",
                "pageable",
                "last",
                "totalElements",
                "totalPages",
                "first",
                "size",
                "number",
                "sort",
                "numberOfElements",
                "empty",
                "summary"
        );
        assertThat(response).doesNotContainKeys("items", "meta");
        assertThat(response.get("totalElements")).isEqualTo(2L);
        assertThat(response.get("numberOfElements")).isEqualTo(1);
        assertThat(response.get("number")).isEqualTo(1);
        assertThat(response.get("size")).isEqualTo(1);
        assertThat((List<?>) response.get("content")).hasSize(1);

        Map<?, ?> pageable = (Map<?, ?>) response.get("pageable");
        assertThat(pageable.get("pageNumber")).isEqualTo(1);
        assertThat(pageable.get("pageSize")).isEqualTo(1);

        Map<?, ?> summary = (Map<?, ?>) response.get("summary");
        assertThat(summary.get("total")).isEqualTo(2);
        assertThat(summary.get("unread")).isEqualTo(1L);
        assertThat(summary.get("dueSoon")).isEqualTo(1L);
        assertThat(summary.get("overdue")).isEqualTo(1L);
    }

    private static NotificationDto notification(NotificationStatus status,
                                                NotificationSeverity severity,
                                                String entityType) {
        return new NotificationDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Title",
                "Message",
                NotificationChannel.WEB,
                status,
                severity,
                entityType,
                UUID.randomUUID().toString(),
                status == NotificationStatus.READ ? Instant.now() : null
        );
    }
}

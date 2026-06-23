package com.toir.controller;

import com.toir.dto.common.PageResponseWithSummary;
import com.toir.dto.notification.FinancialReviewInboxFilter;
import com.toir.dto.notification.FinancialReviewInboxSummary;
import com.toir.dto.notification.NotificationDto;
import com.toir.dto.notification.NotificationSummaryDto;
import com.toir.dto.sla.SlaRuleDto;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.enums.SlaEntityType;
import com.toir.enums.SlaTriggerType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUserArgumentResolver;
import com.toir.service.NotificationFacadeService;
import com.toir.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerContractTest {

    @Mock
    NotificationService service;

    @Mock
    NotificationFacadeService notificationFacadeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(service, notificationFacadeService))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listWithEmptyDatasetReturns200StablePage() throws Exception {
        UUID recipientId = UUID.randomUUID();
        when(notificationFacadeService.list(recipientId, 0, 10, null, null, null, null, false))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("recipientId", recipientId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listWithNullSeverityReturnsInfoFallback() throws Exception {
        UUID recipientId = UUID.randomUUID();
        NotificationDto dto = new NotificationDto(
                UUID.randomUUID(),
                recipientId,
                "Title",
                "Message",
                null,
                NotificationStatus.SENT,
                null,
                "WorkOrder",
                "id-1",
                null
        );
        when(notificationFacadeService.list(eq(recipientId), eq(0), eq(10), isNull(), isNull(), isNull(), isNull(), eq(false)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("recipientId", recipientId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].severity").value(NotificationSeverity.INFO.name()));
    }

    @Test
    void listForwardsSupportedFilters() throws Exception {
        UUID recipientId = UUID.randomUUID();
        when(notificationFacadeService.list(
                eq(recipientId),
                eq(1),
                eq(25),
                eq("pump"),
                eq(NotificationStatus.SENT),
                eq(NotificationSeverity.WARNING),
                eq("WorkOrder"),
                eq(true)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 25), 0));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("recipientId", recipientId.toString())
                        .param("page", "1")
                        .param("size", "25")
                        .param("search", "pump")
                        .param("status", "SENT")
                        .param("severity", "WARNING")
                        .param("entityType", "WorkOrder")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk());

        verify(notificationFacadeService).list(
                recipientId,
                1,
                25,
                "pump",
                NotificationStatus.SENT,
                NotificationSeverity.WARNING,
                "WorkOrder",
                true
        );
    }

    @Test
    void summaryReturns200WithStableFields() throws Exception {
        when(notificationFacadeService.summary(null))
                .thenReturn(new NotificationSummaryDto(3, 1, 2, 4, 5, 6));

        mockMvc.perform(get("/api/v1/notifications/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(3))
                .andExpect(jsonPath("$.critical").value(1))
                .andExpect(jsonPath("$.openEscalations").value(2))
                .andExpect(jsonPath("$.financialReviewQueue").value(4))
                .andExpect(jsonPath("$.financialReviewDueSoon").value(5))
                .andExpect(jsonPath("$.financialReviewOverdue").value(6));
    }

    @Test
    void unreadCountReturns200ForEmptyRecipientContext() throws Exception {
        when(notificationFacadeService.unreadCount(null)).thenReturn(0L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0));
    }

    @Test
    void slaRulesIncludeThresholdAndEscalationsArray() throws Exception {
        SlaRuleDto rule = new SlaRuleDto(
                UUID.randomUUID(),
                "SLA-001",
                "Work Order overdue",
                SlaEntityType.WORK_ORDER,
                SlaTriggerType.WORK_ORDER_OVERDUE,
                24,
                null,
                true
        );
        when(notificationFacadeService.slaRules(0, 10))
                .thenReturn(new PageImpl<>(List.of(rule), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/notifications/sla-rules")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].thresholdHours").value(24))
                .andExpect(jsonPath("$.content[0].ruleThreshold").value(24))
                .andExpect(jsonPath("$.content[0].ruleEscalations").isArray())
                .andExpect(jsonPath("$.content[0].ruleEscalations.length()").value(0));
    }

    @Test
    void slaRulesEmptyReturns200WithStablePage() throws Exception {
        when(notificationFacadeService.slaRules(0, 10))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/v1/notifications/sla-rules")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listWithInvalidRecipientIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("recipientId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void nonAdminCannotListAnotherUsersNotifications() throws Exception {
        UUID currentUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        authenticate(currentUserId, "MAINTENANCE_FOREMAN", List.of("NOTIFICATION_READ"));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("recipientId", otherUserId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notificationFacadeService);
    }

    @Test
    void markReadPassesCurrentRecipientScopeToService() throws Exception {
        UUID currentUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        authenticate(currentUserId, "MAINTENANCE_FOREMAN", List.of("NOTIFICATION_MARK_READ"));
        NotificationDto dto = new NotificationDto(
                notificationId,
                currentUserId,
                "Title",
                "Message",
                null,
                NotificationStatus.READ,
                null,
                "RepairRequest",
                UUID.randomUUID().toString(),
                null
        );
        when(service.markRead(notificationId, currentUserId, false)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/notifications/{id}/read", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(NotificationStatus.READ.name()));
    }

    @Test
    void financialReviewInboxBulkReadRouteExists() throws Exception {
        authenticate(UUID.randomUUID(), "SYSTEM_ADMIN", List.of("*"));

        mockMvc.perform(post("/api/v1/notifications/financial-review-inbox/bulk-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void financialReviewInboxAcknowledgeRouteExists() throws Exception {
        authenticate(UUID.randomUUID(), "SYSTEM_ADMIN", List.of("*"));

        mockMvc.perform(post("/api/v1/notifications/financial-review-inbox/{id}/acknowledge", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Seen\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void financialReviewInboxForwardsFrontendFilters() throws Exception {
        UUID currentUserId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        authenticate(currentUserId, "SYSTEM_ADMIN", List.of("*"));
        FinancialReviewInboxFilter expectedFilter = new FinancialReviewInboxFilter(
                "pump",
                "DUE_SOON",
                departmentId,
                "FINANCE_MANAGER",
                "UNACKNOWLEDGED",
                true
        );
        when(notificationFacadeService.financialReviewInbox(
                eq(currentUserId),
                eq(2),
                eq(25),
                eq(expectedFilter)
        )).thenReturn(PageResponseWithSummary.of(
                List.of(),
                2,
                25,
                new FinancialReviewInboxSummary(0, 0, 0, 0, 0, 0)
        ));

        mockMvc.perform(get("/api/v1/notifications/financial-review-inbox")
                        .param("page", "2")
                        .param("size", "25")
                        .param("search", "pump")
                        .param("kind", "DUE_SOON")
                        .param("departmentId", departmentId.toString())
                        .param("recipientRoleCode", "FINANCE_MANAGER")
                        .param("acknowledgementMode", "UNACKNOWLEDGED")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk());

        verify(notificationFacadeService).financialReviewInbox(
                currentUserId,
                2,
                25,
                expectedFilter
        );
    }

    private void authenticate(UUID userId, String primaryRoleCode, List<String> permissions) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId.toString(),
                "user",
                "user@example.test",
                "User",
                UUID.randomUUID().toString(),
                primaryRoleCode,
                permissions
        );
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                user,
                "n/a",
                permissions.toArray(String[]::new)
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

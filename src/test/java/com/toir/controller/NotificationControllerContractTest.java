package com.toir.controller;

import com.toir.dto.notification.NotificationDto;
import com.toir.dto.notification.NotificationSummaryDto;
import com.toir.dto.sla.SlaRuleDto;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.enums.SlaEntityType;
import com.toir.enums.SlaTriggerType;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.CurrentUserArgumentResolver;
import com.toir.service.NotificationFacadeService;
import com.toir.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void listWithEmptyDatasetReturns200StablePage() throws Exception {
        UUID recipientId = UUID.randomUUID();
        when(notificationFacadeService.list(recipientId, 0, 10))
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
        when(notificationFacadeService.list(eq(recipientId), eq(0), eq(10)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("recipientId", recipientId.toString())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].severity").value(NotificationSeverity.INFO.name()));
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
}

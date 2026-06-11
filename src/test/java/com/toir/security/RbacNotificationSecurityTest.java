package com.toir.security;

import com.toir.controller.NotificationController;
import com.toir.dto.notification.NotificationDispatchResponse;
import com.toir.dto.notification.NotificationDto;
import com.toir.dto.notification.NotificationEvaluationResponse;
import com.toir.dto.notification.NotificationSummaryDto;
import com.toir.enums.NotificationChannel;
import com.toir.enums.NotificationSeverity;
import com.toir.enums.NotificationStatus;
import com.toir.service.NotificationFacadeService;
import com.toir.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacNotificationSecurityTest.SecurityBeans.class
})
class RbacNotificationSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    NotificationService notificationService;

    @MockBean
    NotificationFacadeService notificationFacadeService;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return properties;
        }
    }

    @Test
    void unauthenticatedCannotReadNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.NOTIFICATION_READ)
    void notificationReadCanReadListSummaryAndUnreadCount() throws Exception {
        when(notificationFacadeService.list(null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(notificationFacadeService.summary(null)).thenReturn(new NotificationSummaryDto(0, 0, 0, 0, 0, 0));
        when(notificationFacadeService.unreadCount(null)).thenReturn(0L);

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications/summary"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.NOTIFICATION_READ)
    void notificationReadCannotCreateEvaluateOrDispatchNotifications() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/notifications/evaluate"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/notifications/dispatch-pending"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.NOTIFICATION_ADMIN)
    void notificationAdminCanCreateEvaluateAndDispatchNotifications() throws Exception {
        UUID notificationId = UUID.randomUUID();
        when(notificationService.send(any())).thenReturn(notificationDto(notificationId));
        when(notificationFacadeService.evaluate()).thenReturn(new NotificationEvaluationResponse(0, 0, 0));
        when(notificationFacadeService.dispatch()).thenReturn(new NotificationDispatchResponse(0));

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationPayload()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/notifications/evaluate"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/notifications/dispatch-pending"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.NOTIFICATION_MARK_READ)
    void notificationMarkReadCanMarkRead() throws Exception {
        UUID notificationId = UUID.randomUUID();
        when(notificationService.markRead(eq(notificationId), any(), eq(false))).thenReturn(notificationDto(notificationId));

        mockMvc.perform(post("/api/v1/notifications/{id}/read", notificationId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.NOTIFICATION_READ)
    void notificationReadCannotMarkRead() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/{id}/read", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    private String notificationPayload() {
        return """
                {
                  "recipientId": "%s",
                  "title": "Title",
                  "message": "Message",
                  "channel": "WEB",
                  "severity": "INFO",
                  "entityType": "WorkOrder",
                  "entityId": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private NotificationDto notificationDto(UUID id) {
        return new NotificationDto(
                id,
                UUID.randomUUID(),
                "Title",
                "Message",
                NotificationChannel.WEB,
                NotificationStatus.SENT,
                NotificationSeverity.INFO,
                "WorkOrder",
                UUID.randomUUID().toString(),
                null
        );
    }
}

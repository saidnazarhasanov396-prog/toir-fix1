package com.toir.security;

import com.toir.controller.NotificationController;
import com.toir.dto.notification.NotificationDispatchResponse;
import com.toir.dto.notification.NotificationEvaluationResponse;
import com.toir.dto.notification.NotificationSummaryDto;
import com.toir.service.NotificationFacadeService;
import com.toir.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

    @BeforeEach
    void setUp() {
        when(notificationFacadeService.list(any(), eq(0), eq(20), any(), any(), any(), any(), eq(false))).thenReturn(Page.empty());
        when(notificationFacadeService.summary(any())).thenReturn(new NotificationSummaryDto(0, 0, 0, 0, 0, 0));
        when(notificationFacadeService.unreadCount(any())).thenReturn(0L);
        when(notificationFacadeService.slaRules(0, 20)).thenReturn(Page.empty());
        when(notificationFacadeService.evaluate()).thenReturn(new NotificationEvaluationResponse(0, 0, 0));
        when(notificationFacadeService.dispatch()).thenReturn(new NotificationDispatchResponse(0));
    }

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
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.NOTIFICATION_READ)
    void notificationReadCanReadInboxSummaryAndUnreadCount() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications/summary"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.NOTIFICATION_READ)
    void notificationReadCannotMarkReadOrRunAdminEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/00000000-0000-0000-0000-000000000001/read"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/notifications/evaluate"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/notifications/dispatch-pending"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.NOTIFICATION_MARK_READ)
    void notificationMarkReadCanMarkReadOnly() throws Exception {
        when(notificationService.markRead(any(), any(), eq(false))).thenReturn(null);

        mockMvc.perform(post("/api/v1/notifications/00000000-0000-0000-0000-000000000001/read"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.NOTIFICATION_ADMIN)
    void notificationAdminCanRunAdminEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientId": "00000000-0000-0000-0000-000000000002",
                                  "title": "Manual",
                                  "message": "Admin notification"
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/notifications/sla-rules"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/notifications/evaluate"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/notifications/dispatch-pending"))
                .andExpect(status().isOk());
    }
}

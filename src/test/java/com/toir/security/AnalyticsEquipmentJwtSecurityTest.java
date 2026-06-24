package com.toir.security;

import com.toir.controller.AnalyticsController;
import com.toir.dto.analytics.EquipmentAnalyticsResponse;
import com.toir.service.AnalyticsService;
import com.toir.service.ReliabilityPassportService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnalyticsController.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cors.allowed-origin-patterns=http://localhost:3000")
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class
})
class AnalyticsEquipmentJwtSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    AnalyticsService analyticsService;

    @MockBean
    ReliabilityPassportService reliabilityPassportService;

    @Test
    void endpointAcceptsTokenWithAnalyticsReadPermission() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(jwtService.parse("analytics-token")).thenReturn(claims(
                List.of("WORKSHOP_HEAD"),
                List.of(PermissionConstants.ANALYTICS_READ),
                "WORKSHOP_HEAD"
        ));
        when(analyticsService.equipmentAnalytics(equipmentId)).thenReturn(response(equipmentId));

        mockMvc.perform(get("/api/v1/analytics/equipment/{equipmentId}", equipmentId)
                        .header("Authorization", "Bearer analytics-token"))
                .andExpect(status().isOk());

        verify(analyticsService).equipmentAnalytics(eq(equipmentId));
    }

    @Test
    void endpointAcceptsRoleOnlyRefreshedTokenForKnownAnalyticsRole() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(jwtService.parse("refreshed-role-only-token")).thenReturn(claims(
                List.of("WORKSHOP_HEAD"),
                null,
                "WORKSHOP_HEAD"
        ));
        when(analyticsService.equipmentAnalytics(equipmentId)).thenReturn(response(equipmentId));

        mockMvc.perform(get("/api/v1/analytics/equipment/{equipmentId}", equipmentId)
                        .header("Authorization", "Bearer refreshed-role-only-token"))
                .andExpect(status().isOk());

        verify(analyticsService).equipmentAnalytics(eq(equipmentId));
    }

    @Test
    void endpointRejectsTokenWithoutAnalyticsReadOrAllowedRole() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        when(jwtService.parse("no-analytics-token")).thenReturn(claims(
                List.of("CUSTOM_ROLE"),
                List.of(PermissionConstants.EQUIPMENT_READ),
                "CUSTOM_ROLE"
        ));

        mockMvc.perform(get("/api/v1/analytics/equipment/{equipmentId}", equipmentId)
                        .header("Authorization", "Bearer no-analytics-token"))
                .andExpect(status().isForbidden());

        verify(analyticsService, never()).equipmentAnalytics(any(UUID.class));
    }

    private Claims claims(List<String> authorities, List<String> permissions, String primaryRoleCode) {
        return Jwts.claims()
                .subject("user-id")
                .add(Map.of(
                        "username", "user",
                        "email", "user@example.com",
                        "fullName", "User",
                        "primaryRoleCode", primaryRoleCode,
                        "authorities", authorities,
                        "permissions", permissions == null ? List.of() : permissions
                ))
                .build();
    }

    private EquipmentAnalyticsResponse response(UUID equipmentId) {
        return new EquipmentAnalyticsResponse(
                equipmentId.toString(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }
}

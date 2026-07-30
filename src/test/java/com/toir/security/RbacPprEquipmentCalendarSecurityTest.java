package com.toir.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toir.controller.PprEquipmentCalendarController;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarAuthoritativeSource;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarExcludedDiagnostics;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarPageMetadata;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarPlacementBasis;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarPlanSummary;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarResponse;
import com.toir.dto.pprplanning.calendar.PprEquipmentCalendarSpanMode;
import com.toir.enums.PlanStatus;
import com.toir.service.pprcalendar.PprEquipmentCalendarService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PprEquipmentCalendarController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacPprEquipmentCalendarSecurityTest.SecurityBeans.class
})
class RbacPprEquipmentCalendarSecurityTest {

    private static final UUID PLAN_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired MockMvc mockMvc;
    @MockBean JwtService jwtService;
    @MockBean PprEquipmentCalendarService service;

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
    void unauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/ppr-plans/{planId}/equipment-calendar", PLAN_ID)
                        .param("year", "2026"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_PLAN_READ)
    void planReadAloneCannotReadEquipmentCalendar() throws Exception {
        mockMvc.perform(get("/api/v1/ppr-plans/{planId}/equipment-calendar", PLAN_ID)
                        .param("year", "2026"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_TASK_READ)
    void taskReadAloneCannotReadEquipmentCalendar() throws Exception {
        mockMvc.perform(get("/api/v1/ppr-plans/{planId}/equipment-calendar", PLAN_ID)
                        .param("year", "2026"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PPR_CALENDAR_READ)
    void pprCalendarReadCanReadEquipmentCalendar() throws Exception {
        when(service.getCalendar(eq(PLAN_ID), any())).thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/ppr-plans/{planId}/equipment-calendar", PLAN_ID)
                        .param("year", "2026"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadEquipmentCalendar() throws Exception {
        when(service.getCalendar(eq(PLAN_ID), any())).thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/ppr-plans/{planId}/equipment-calendar", PLAN_ID)
                        .param("year", "2026"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadEquipmentCalendar() throws Exception {
        when(service.getCalendar(eq(PLAN_ID), any())).thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v1/ppr-plans/{planId}/equipment-calendar", PLAN_ID)
                        .param("year", "2026"))
                .andExpect(status().isOk());
    }

    private PprEquipmentCalendarResponse emptyResponse() {
        return new PprEquipmentCalendarResponse(
                new PprEquipmentCalendarPlanSummary(
                        PLAN_ID,
                        "PPR-2026",
                        "Year plan",
                        PlanStatus.APPROVED,
                        null,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31)),
                2026,
                PprEquipmentCalendarAuthoritativeSource.PPR_TASK,
                null,
                PprEquipmentCalendarPlacementBasis.PLANNED_DATE,
                PprEquipmentCalendarSpanMode.START_MONTH,
                new PprEquipmentCalendarPageMetadata(0, 25, 0, 0),
                List.of(),
                new PprEquipmentCalendarExcludedDiagnostics(0, 0, 0, 0));
    }
}

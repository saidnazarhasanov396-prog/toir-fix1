package com.toir.security;

import com.toir.controller.LaborEntryController;
import com.toir.dto.laborentry.LaborEntryDto;
import com.toir.service.LaborEntryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LaborEntryController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacLaborEntrySecurityTest.SecurityBeans.class
})
class RbacLaborEntrySecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    LaborEntryService laborEntryService;

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
    void unauthenticatedCannotReadLaborEntries() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/labor", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void workOrderReadCanReadLaborEntries() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(laborEntryService.findByWorkOrder(workOrderId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/labor", workOrderId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void readOnlyUserCannotCreateLaborEntry() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/labor", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(laborPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.TIMESHEET_CREATE)
    void timesheetCreateCanCreateLaborEntry() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(laborEntryService.create(eq(workOrderId), any(LaborEntryDto.class))).thenReturn(laborDto(workOrderId));

        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/labor", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(laborPayload()))
                .andExpect(status().isCreated());
    }

    private String laborPayload() {
        return """
                {
                  "userId": "%s",
                  "workDate": "2026-06-06",
                  "hours": 2,
                  "rate": 100000,
                  "description": "Pump repair labor"
                }
                """.formatted(UUID.randomUUID());
    }

    private LaborEntryDto laborDto(UUID workOrderId) {
        return new LaborEntryDto(
                UUID.randomUUID(),
                workOrderId,
                UUID.randomUUID(),
                null,
                null,
                LocalDate.of(2026, 6, 6),
                2,
                100000.0,
                "Pump repair labor"
        );
    }
}

package com.toir.security;

import com.toir.controller.WorkOrderCloseReadinessController;
import com.toir.dto.workorder.WorkOrderCloseReadinessDto;
import com.toir.dto.workorder.WorkOrderCloseReadinessItemDto;
import com.toir.enums.CloseReadinessGroupStatus;
import com.toir.enums.CloseReadinessSeverity;
import com.toir.enums.WorkOrderStatus;
import com.toir.exception.RestException;
import com.toir.service.WorkOrderService;
import java.time.Instant;
import java.util.LinkedHashMap;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WorkOrderCloseReadinessController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWorkOrderCloseReadinessSecurityTest.SecurityBeans.class
})
class RbacWorkOrderCloseReadinessSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WorkOrderService workOrderService;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties props = new CorsProperties();
            props.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return props;
        }
    }

    @Test
    void unauthenticatedCannotReadCloseReadiness() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders/{id}/close-readiness", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadCloseReadiness() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders/{id}/close-readiness", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void workOrderReadCanReadCloseReadinessJsonShape() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        LinkedHashMap<String, CloseReadinessGroupStatus> groups = new LinkedHashMap<>();
        groups.put("tasks", CloseReadinessGroupStatus.READY);
        groups.put("acts", CloseReadinessGroupStatus.BLOCKED);
        groups.put("materials", CloseReadinessGroupStatus.WARNING);
        when(workOrderService.getCloseReadiness(workOrderId)).thenReturn(new WorkOrderCloseReadinessDto(
                workOrderId,
                WorkOrderStatus.COMPLETED.name(),
                false,
                Instant.parse("2026-07-06T10:00:00Z"),
                List.of(new WorkOrderCloseReadinessItemDto(
                        "FINAL_ACCEPTANCE_NOT_ACCEPTED",
                        "Final repair acceptance must be ACCEPTED.",
                        CloseReadinessSeverity.BLOCKING,
                        "acts",
                        "review-acceptance",
                        "closure"
                )),
                List.of(new WorkOrderCloseReadinessItemDto(
                        "PENDING_ACTUAL_COSTS",
                        "Pending actual costs require finance review.",
                        CloseReadinessSeverity.WARNING,
                        "finance",
                        "review-costs",
                        "finance"
                )),
                groups
        ));

        mockMvc.perform(get("/api/v1/work-orders/{id}/close-readiness", workOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workOrderId").value(workOrderId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.checkedAt").value("2026-07-06T10:00:00Z"))
                .andExpect(jsonPath("$.blockers[0].code").value("FINAL_ACCEPTANCE_NOT_ACCEPTED"))
                .andExpect(jsonPath("$.blockers[0].severity").value("BLOCKING"))
                .andExpect(jsonPath("$.blockers[0].group").value("acts"))
                .andExpect(jsonPath("$.blockers[0].targetAction").value("review-acceptance"))
                .andExpect(jsonPath("$.blockers[0].targetTab").value("closure"))
                .andExpect(jsonPath("$.warnings[0].code").value("PENDING_ACTUAL_COSTS"))
                .andExpect(jsonPath("$.warnings[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.groups.tasks").value("READY"))
                .andExpect(jsonPath("$.groups.acts").value("BLOCKED"))
                .andExpect(jsonPath("$.groups.materials").value("WARNING"));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WORK_ORDER_READ)
    void missingWorkOrderReturnsNotFound() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(workOrderService.getCloseReadiness(workOrderId))
                .thenThrow(RestException.notFound("Work order not found: " + workOrderId));

        mockMvc.perform(get("/api/v1/work-orders/{id}/close-readiness", workOrderId))
                .andExpect(status().isNotFound());
    }
}

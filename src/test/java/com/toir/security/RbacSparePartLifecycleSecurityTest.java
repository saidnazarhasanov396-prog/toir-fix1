package com.toir.security;

import com.toir.controller.sparepartlifecycle.SparePartDueEventController;
import com.toir.controller.sparepartlifecycle.SparePartInstallationController;
import com.toir.controller.sparepartlifecycle.SparePartLifeRuleController;
import com.toir.service.sparepartlifecycle.SparePartDueEventService;
import com.toir.service.sparepartlifecycle.SparePartLifecycleEvaluationService;
import com.toir.service.sparepartlifecycle.SparePartLifecycleService;
import com.toir.service.sparepartlifecycle.SparePartLifeRuleService;
import java.util.List;
import java.util.UUID;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        SparePartInstallationController.class,
        SparePartLifeRuleController.class,
        SparePartDueEventController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacSparePartLifecycleSecurityTest.SecurityBeans.class
})
class RbacSparePartLifecycleSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockBean JwtService jwtService;
    @MockBean SparePartLifecycleService lifecycleService;
    @MockBean SparePartLifecycleEvaluationService evaluationService;
    @MockBean SparePartLifeRuleService ruleService;
    @MockBean SparePartDueEventService dueEventService;
    @MockBean ScopeAccessService scopeAccessService;

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
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCallAnyLifecycleWriteEndpoint() throws Exception {
        UUID equipmentId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        String installPayload = """
                {"slotCode":"A","sparePartId":"%s","quantity":1,"externalSourceReason":"test"}
                """.formatted(UUID.randomUUID());
        String removePayload = """
                {"installationId":"%s","disposition":"SCRAP","reason":"test"}
                """.formatted(id);
        String replacePayload = """
                {"oldInstallationId":"%s","newPart":{"sparePartId":"%s","quantity":1,"externalSourceReason":"test"},"oldPartDisposition":"SCRAP","reason":"test"}
                """.formatted(id, UUID.randomUUID());
        String rulePayload = """
                {"sparePartId":"%s","combinationMode":"MANUAL","dueAction":"WARNING_ONLY","limits":[]}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/spare-part-installations/equipment/{equipmentId}", equipmentId)
                        .header("Idempotency-Key", "test-install")
                        .contentType(MediaType.APPLICATION_JSON).content(installPayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/spare-part-installations/{id}/remove", id)
                        .header("Idempotency-Key", "test-remove")
                        .contentType(MediaType.APPLICATION_JSON).content(removePayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/spare-part-installations/{id}/replace", id)
                        .header("Idempotency-Key", "test-replace")
                        .contentType(MediaType.APPLICATION_JSON).content(replacePayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/spare-part-installations/{id}/reevaluate", id))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/spare-part-life-rules")
                        .contentType(MediaType.APPLICATION_JSON).content(rulePayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/spare-part-life-rules/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(rulePayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/spare-part-life-rules/{id}", id))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/spare-part-due-events/{id}/acknowledge", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}

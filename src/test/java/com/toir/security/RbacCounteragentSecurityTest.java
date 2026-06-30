package com.toir.security;

import com.toir.controller.CounteragentController;
import com.toir.controller.counteragent.CounteragentContractController;
import com.toir.controller.counteragent.CounteragentWorkController;
import com.toir.dto.counteragent.CounteragentDto;
import com.toir.enums.CounteragentStatus;
import com.toir.service.CounteragentService;
import com.toir.service.counteragent.CounteragentContractService;
import com.toir.service.counteragent.CounteragentWorkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        CounteragentController.class,
        CounteragentWorkController.class,
        CounteragentContractController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacCounteragentSecurityTest.SecurityBeans.class
})
class RbacCounteragentSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;
    @MockBean
    CounteragentService counteragentService;
    @MockBean
    CounteragentWorkService counteragentWorkService;
    @MockBean
    CounteragentContractService counteragentContractService;

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
    void unauthenticatedCannotReadCounteragents() throws Exception {
        mockMvc.perform(get("/api/v1/counteragents?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.PROCUREMENT_READ)
    void procurementReadAloneCannotReadCounteragents() throws Exception {
        mockMvc.perform(get("/api/v1/counteragents?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadCounteragentsWithContent() throws Exception {
        UUID counteragentId = UUID.randomUUID();
        when(counteragentService.findAll(isNull(), isNull())).thenReturn(List.of(
                new CounteragentDto(counteragentId, "CA-ADMIN", "Admin Counteragent", null, null, null, null, null, null, null, null, null, null, null, CounteragentStatus.ACTIVE)
        ));

        mockMvc.perform(get("/api/v1/counteragents?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(counteragentId.toString()));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.COUNTERAGENT_READ)
    void counteragentReadCanReadCounteragentsAndRelatedRegisters() throws Exception {
        UUID counteragentId = UUID.randomUUID();
        when(counteragentService.findAll(isNull(), isNull())).thenReturn(List.of(
                new CounteragentDto(counteragentId, "CA-1", "Counteragent", null, null, null, null, null, null, null, null, null, null, null, CounteragentStatus.ACTIVE)
        ));
        when(counteragentWorkService.findByCounteragent(any())).thenReturn(List.of());
        when(counteragentContractService.findByCounteragent(counteragentId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/counteragents?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(counteragentId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/counteragent-works?counteragentId={counteragentId}&page=0&size=1", counteragentId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/counteragent-contracts?counteragentId={counteragentId}&page=0&size=1", counteragentId))
                .andExpect(status().isOk());
    }
}

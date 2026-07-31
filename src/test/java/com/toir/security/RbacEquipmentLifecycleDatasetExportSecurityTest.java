package com.toir.security;

import com.toir.controller.EquipmentLifecycleDatasetExportController;
import com.toir.dto.equipmentlifecycleexport.EquipmentLifecycleExportResponses.JobResponse;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportScopeMode;
import com.toir.enums.equipmentlifecycleexport.EquipmentLifecycleExportStatus;
import com.toir.service.equipmentlifecycleexport.EquipmentLifecycleExportJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@WebMvcTest(controllers = EquipmentLifecycleDatasetExportController.class)
@TestPropertySource(properties = "toir.ai.equipment-lifecycle.export.enabled=true")
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacEquipmentLifecycleDatasetExportSecurityTest.SecurityBeans.class
})
class RbacEquipmentLifecycleDatasetExportSecurityTest {
    private static final String BASE = "/api/v1/ai/equipment-lifecycle/dataset-exports";

    @Autowired
    MockMvc mockMvc;
    @MockBean
    JwtService jwtService;
    @MockBean
    EquipmentLifecycleExportJobService service;

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
    void unauthenticatedCannotListExports() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE)
                        .header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"standard-v1\",\"scope\":{\"mode\":\"ALL_AUTHORIZED\"}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_READ)
    void ordinaryEquipmentReaderCannotUseAnyExportOperation() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post(BASE)
                        .header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"standard-v1\",\"scope\":{\"mode\":\"ALL_AUTHORIZED\"}}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + id + "/resume")).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + id + "/cancel")).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + id + "/artifacts")).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + id + "/artifacts/DATASET")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.EQUIPMENT_LIFECYCLE_DATASET_EXPORT)
    void dedicatedPermissionCanListExports() throws Exception {
        when(service.list(any(), anyInt(), anyInt(), any())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get(BASE)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadStatus() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(eq(id), any())).thenReturn(response(id));
        mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanListExports() throws Exception {
        when(service.list(any(), anyInt(), anyInt(), any())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get(BASE)).andExpect(status().isOk());
    }

    private JobResponse response(UUID id) {
        Instant now = Instant.parse("2026-07-31T10:00:00Z");
        return new JobResponse(id, EquipmentLifecycleExportStatus.QUEUED, now, "standard-v1",
                EquipmentLifecycleExportScopeMode.ALL_AUTHORIZED, false, 0, 0, now, null, null,
                null, null, null, null, false, BASE + "/" + id, List.of());
    }
}

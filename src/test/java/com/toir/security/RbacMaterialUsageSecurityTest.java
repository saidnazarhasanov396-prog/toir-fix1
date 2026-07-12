package com.toir.security;

import com.toir.controller.repair.RepairMaterialUsageController;
import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.service.repair.RepairMaterialUsageService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RepairMaterialUsageController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacMaterialUsageSecurityTest.SecurityBeans.class
})
class RbacMaterialUsageSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    RepairMaterialUsageService repairMaterialUsageService;

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
    void unauthenticatedCannotReadMaterialUsage() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/material-usage", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadMaterialUsage() throws Exception {
        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/material-usage", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MATERIAL_USAGE_READ)
    void materialUsageReadCanReadMaterialUsage() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(repairMaterialUsageService.findByWorkOrder(workOrderId)).thenReturn(List.of(materialUsageDto(workOrderId)));

        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/material-usage", workOrderId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MATERIAL_USAGE_READ)
    void materialUsageReadCanReadRepairRequestAggregatedMaterialUsage() throws Exception {
        UUID repairRequestId = UUID.randomUUID();
        when(repairMaterialUsageService.findByRepairRequest(repairRequestId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/repair-requests/{repairRequestId}/material-usage", repairRequestId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MATERIAL_USAGE_READ)
    void materialUsageReadCanReadPprTaskAggregatedMaterialUsage() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(repairMaterialUsageService.findByPprTask(taskId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/ppr-plans/tasks/{taskId}/material-usage", taskId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadMaterialUsage() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(repairMaterialUsageService.findByWorkOrder(workOrderId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/material-usage", workOrderId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadMaterialUsage() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(repairMaterialUsageService.findByWorkOrder(workOrderId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/work-orders/{workOrderId}/material-usage", workOrderId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MATERIAL_USAGE_ISSUE)
    void materialUsageIssueCanRegisterMaterialUsage() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(repairMaterialUsageService.register(eq(workOrderId), any())).thenReturn(materialUsageDto(workOrderId));

        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/material-usage", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialUsagePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MATERIAL_USAGE_READ)
    void materialUsageReadCannotRegisterMaterialUsage() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/material-usage", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialUsagePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotRegisterMaterialUsage() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/material-usage", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialUsagePayload()))
                .andExpect(status().isForbidden());
    }

    private RepairMaterialUsageDto materialUsageDto(UUID workOrderId) {
        return new RepairMaterialUsageDto(
                UUID.randomUUID(),
                workOrderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                java.math.BigDecimal.ONE,
                10.0
        );
    }

    private String materialUsagePayload() {
        return """
                {
                  "warehouseId": "%s",
                  "sparePartId": "%s",
                  "quantity": 1,
                  "unitCost": 10
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }
}

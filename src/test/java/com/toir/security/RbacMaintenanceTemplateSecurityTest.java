package com.toir.security;

import com.toir.controller.maintenance.MaintenanceTemplateController;
import com.toir.dto.maintenancetemplate.MaintenanceOperationDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateDto;
import com.toir.dto.maintenancetemplate.MaintenanceTemplateStatsResponse;
import com.toir.enums.MaintenanceKind;
import com.toir.service.maintanance.MaintenanceTemplateService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MaintenanceTemplateController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacMaintenanceTemplateSecurityTest.SecurityBeans.class
})
class RbacMaintenanceTemplateSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    MaintenanceTemplateService maintenanceTemplateService;

    @MockBean
    ScopeAccessService scopeAccessService;

    @BeforeEach
    void setUp() {
        lenient().when(scopeAccessService.enforceDepartmentScope(isNull())).thenReturn(null);
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
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadTemplates() throws Exception {
        UUID templateId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/maintenance-templates/stats"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/maintenance-templates?page=0&size=1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/maintenance-templates/{id}", templateId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_REGULATION_READ)
    void regulationReadCanReadTemplates() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(maintenanceTemplateService.getStats(isNull(), isNull()))
                .thenReturn(new MaintenanceTemplateStatsResponse(1, 1, 1, 1.0f));
        when(maintenanceTemplateService.findAll(isNull(), isNull()))
                .thenReturn(List.of(templateDto(templateId)));
        when(maintenanceTemplateService.findById(templateId)).thenReturn(templateDto(templateId));

        mockMvc.perform(get("/api/v1/maintenance-templates/stats"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/maintenance-templates?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/maintenance-templates/{id}", templateId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCreateTemplate() throws Exception {
        mockMvc.perform(post("/api/v1/maintenance-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templatePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_REGULATION_CREATE)
    void regulationCreateCanCreateTemplate() throws Exception {
        UUID templateId = UUID.randomUUID();
        when(maintenanceTemplateService.create(any())).thenReturn(templateDto(templateId));

        mockMvc.perform(post("/api/v1/maintenance-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templatePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotUpdateTemplateOrOperations() throws Exception {
        UUID templateId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/maintenance-templates/{id}", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templatePayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/maintenance-templates/{id}/operations", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/maintenance-templates/operations/{operationId}", operationId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_REGULATION_UPDATE)
    void regulationUpdateCanUpdateTemplateAndOperations() throws Exception {
        UUID templateId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        when(maintenanceTemplateService.update(eq(templateId), any())).thenReturn(templateDto(templateId));
        when(maintenanceTemplateService.addOperation(eq(templateId), any())).thenReturn(operationDto(operationId));

        mockMvc.perform(put("/api/v1/maintenance-templates/{id}", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templatePayload()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/maintenance-templates/{id}/operations", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationPayload()))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/maintenance-templates/operations/{operationId}", operationId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotDeleteTemplate() throws Exception {
        mockMvc.perform(delete("/api/v1/maintenance-templates/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.MAINTENANCE_REGULATION_DELETE)
    void regulationDeleteCanDeleteTemplate() throws Exception {
        mockMvc.perform(delete("/api/v1/maintenance-templates/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    private MaintenanceTemplateDto templateDto(UUID id) {
        return new MaintenanceTemplateDto(
                id,
                "MT-2026-0001",
                "Pump monthly PM",
                "Template description",
                UUID.randomUUID(),
                "Pump",
                MaintenanceKind.PREVENTIVE,
                4.5,
                true,
                List.of()
        );
    }

    private MaintenanceOperationDto operationDto(UUID id) {
        return new MaintenanceOperationDto(
                id,
                null,
                null,
                null,
                1,
                "Inspect seal",
                "Inspect seal and housing",
                1.0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private String templatePayload() {
        return """
                {
                  "name": "Pump monthly PM",
                  "description": "Template description",
                  "equipmentTypeId": "%s",
                  "maintenanceKind": "PREVENTIVE",
                  "normativeLaborHours": 4.5,
                  "active": true
                }
                """.formatted(UUID.randomUUID());
    }

    private String operationPayload() {
        return """
                {
                  "sequence": 1,
                  "name": "Inspect seal",
                  "durationHours": 1.0
                }
                """;
    }
}

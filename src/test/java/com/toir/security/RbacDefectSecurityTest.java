package com.toir.security;

import com.toir.controller.defects.DefectController;
import com.toir.controller.defects.DefectDictionariesController;
import com.toir.dto.defect.DefectResponse;
import com.toir.dto.defect.DefectStatsResponse;
import com.toir.entity.KnowledgeArticle;
import com.toir.enums.DefectStatus;
import com.toir.repository.FailureReasonRepository;
import com.toir.repository.RootCauseRepository;
import com.toir.repository.defects.DefectCategoryRepository;
import com.toir.repository.defects.DefectSeverityRepository;
import com.toir.service.defects.DefectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DefectController.class, DefectDictionariesController.class})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacDefectSecurityTest.SecurityBeans.class
})
class RbacDefectSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    DefectService defectService;

    @MockBean
    DefectCategoryRepository categoryRepository;

    @MockBean
    DefectSeverityRepository severityRepository;

    @MockBean
    FailureReasonRepository failureReasonRepository;

    @MockBean
    RootCauseRepository rootCauseRepository;

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
    void unauthenticatedCannotReadDefects() throws Exception {
        mockMvc.perform(get("/api/v1/defects?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadDefects() throws Exception {
        mockMvc.perform(get("/api/v1/defects?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_READ)
    void defectReadCanReadListStatsDetailAndDictionaries() throws Exception {
        UUID defectId = UUID.randomUUID();
        when(defectService.search(null, null, 0, 1, null))
                .thenReturn(new PageImpl<>(List.of(defectResponse(defectId)), PageRequest.of(0, 1), 1));
        when(defectService.getStats(null, null, null))
                .thenReturn(new DefectStatsResponse(1, 1, 0, 0));
        when(defectService.findById(defectId)).thenReturn(defectResponse(defectId));
        when(categoryRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(severityRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(failureReasonRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());
        when(rootCauseRepository.findAllByIsDeletedFalseOrderByUpdatedAtDesc()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/defects?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/defects/stats"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/defects/{id}", defectId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/defects/dictionaries"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadDefects() throws Exception {
        when(defectService.search(null, null, 0, 1, null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/defects?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadDefects() throws Exception {
        when(defectService.search(null, null, 0, 1, null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/defects?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_CREATE)
    void defectCreateCanCreateDefect() throws Exception {
        when(defectService.create(any())).thenReturn(defectResponse(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_READ)
    void defectReadCannotCreateDefect() throws Exception {
        mockMvc.perform(post("/api/v1/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCreateDefect() throws Exception {
        mockMvc.perform(post("/api/v1/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotCreateDefect() throws Exception {
        mockMvc.perform(post("/api/v1/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_UPDATE)
    void defectUpdateCanUpdateDefectAndCreateLesson() throws Exception {
        UUID defectId = UUID.randomUUID();
        when(defectService.update(any(), any())).thenReturn(defectResponse(defectId));
        when(defectService.createLesson(defectId)).thenReturn(new KnowledgeArticle());

        mockMvc.perform(put("/api/v1/defects/{id}", defectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectPayload()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/defects/{id}/create-lesson", defectId))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_READ)
    void defectReadCannotUpdateDefectOrCreateLesson() throws Exception {
        UUID defectId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/defects/{id}", defectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/defects/{id}/create-lesson", defectId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_RESOLVE)
    void defectResolveCanResolveDefect() throws Exception {
        UUID defectId = UUID.randomUUID();
        when(defectService.resolve(defectId)).thenReturn(defectResponse(defectId));

        mockMvc.perform(post("/api/v1/defects/{id}/resolve", defectId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_READ)
    void defectReadCannotResolveDefect() throws Exception {
        mockMvc.perform(post("/api/v1/defects/{id}/resolve", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_DELETE)
    void defectDeleteCanDeleteDefect() throws Exception {
        mockMvc.perform(delete("/api/v1/defects/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_READ)
    void defectReadCannotDeleteDefect() throws Exception {
        mockMvc.perform(delete("/api/v1/defects/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    private DefectResponse defectResponse(UUID id) {
        return new DefectResponse(
                id,
                "DF-2026-1001",
                "Leak",
                "Oil leak",
                UUID.randomUUID(),
                "Pump #1",
                null,
                null,
                "MECHANICAL",
                "MEDIUM",
                null,
                null,
                DefectStatus.OPEN,
                Instant.now(),
                null,
                0,
                null,
                List.of(),
                false
        );
    }

    private String defectPayload() {
        return """
                {
                  "title": "Leak",
                  "description": "Oil leak",
                  "equipmentId": "%s",
                  "category": "MECHANICAL",
                  "severity": "MEDIUM"
                }
                """.formatted(UUID.randomUUID());
    }
}

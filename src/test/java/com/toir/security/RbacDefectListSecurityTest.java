package com.toir.security;

import com.toir.controller.defects.DefectListController;
import com.toir.dto.defectlist.DefectListDto;
import com.toir.dto.defectlist.DefectListLineDto;
import com.toir.dto.defectlist.DefectListStatsResponse;
import com.toir.enums.DefectListStatus;
import com.toir.service.ApprovalService;
import com.toir.service.defects.DefectListService;
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

@WebMvcTest(controllers = DefectListController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacDefectListSecurityTest.SecurityBeans.class
})
class RbacDefectListSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    DefectListService defectListService;

    @MockBean
    ApprovalService approvalService;

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
    void unauthenticatedCannotReadDefectLists() throws Exception {
        mockMvc.perform(get("/api/v1/defect-lists?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadDefectLists() throws Exception {
        mockMvc.perform(get("/api/v1/defect-lists?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_READ)
    void defectListReadCanReadListStatsAndDetail() throws Exception {
        UUID listId = UUID.randomUUID();
        when(defectListService.search(null, 0, 1, null))
                .thenReturn(new PageImpl<>(List.of(defectListDto(listId)), PageRequest.of(0, 1), 1));
        when(defectListService.getStats(null, null))
                .thenReturn(new DefectListStatsResponse(1, 1, 0, 0));
        when(defectListService.findById(listId)).thenReturn(defectListDto(listId));

        mockMvc.perform(get("/api/v1/defect-lists?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/defect-lists/stats"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/defect-lists/{id}", listId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadDefectLists() throws Exception {
        when(defectListService.search(null, 0, 1, null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/defect-lists?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadDefectLists() throws Exception {
        when(defectListService.search(null, 0, 1, null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/defect-lists?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_CREATE)
    void defectListCreateCanCreateDefectList() throws Exception {
        when(defectListService.create(any())).thenReturn(defectListDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/defect-lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectListPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_READ)
    void defectListReadCannotCreateDefectList() throws Exception {
        mockMvc.perform(post("/api/v1/defect-lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectListPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCreateDefectList() throws Exception {
        mockMvc.perform(post("/api/v1/defect-lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectListPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotCreateDefectList() throws Exception {
        mockMvc.perform(post("/api/v1/defect-lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectListPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_UPDATE)
    void defectListUpdateCanUpdateDefectListAndAddLine() throws Exception {
        UUID listId = UUID.randomUUID();
        when(defectListService.update(any(), any())).thenReturn(defectListDto(listId));
        when(defectListService.addLine(any(), any())).thenReturn(defectListLineDto(UUID.randomUUID()));

        mockMvc.perform(put("/api/v1/defect-lists/{id}", listId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectListPayload()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/defect-lists/{id}/lines", listId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_READ)
    void defectListReadCannotUpdateDefectListOrAddLine() throws Exception {
        UUID listId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/defect-lists/{id}", listId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(defectListPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/defect-lists/{id}/lines", listId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_APPROVE)
    void defectListApproveEndpointIsRemoved() throws Exception {
        UUID listId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/defect-lists/{id}/approve", listId)
                        .param("approverId", approverId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_READ)
    void defectListReadCannotApproveDefectList() throws Exception {
        mockMvc.perform(post("/api/v1/defect-lists/{id}/approve", UUID.randomUUID())
                        .param("approverId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_CLOSE)
    void defectListCloseCanCloseDefectList() throws Exception {
        UUID listId = UUID.randomUUID();
        when(defectListService.close(listId)).thenReturn(defectListDto(listId));

        mockMvc.perform(post("/api/v1/defect-lists/{id}/close", listId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_READ)
    void defectListReadCannotCloseDefectList() throws Exception {
        mockMvc.perform(post("/api/v1/defect-lists/{id}/close", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_DELETE)
    void defectListDeleteCanDeleteLine() throws Exception {
        mockMvc.perform(delete("/api/v1/defect-lists/lines/{lineId}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.DEFECT_LIST_READ)
    void defectListReadCannotDeleteLine() throws Exception {
        mockMvc.perform(delete("/api/v1/defect-lists/lines/{lineId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotDeleteLine() throws Exception {
        mockMvc.perform(delete("/api/v1/defect-lists/lines/{lineId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotDeleteLine() throws Exception {
        mockMvc.perform(delete("/api/v1/defect-lists/lines/{lineId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    private DefectListDto defectListDto(UUID id) {
        return new DefectListDto(
                id,
                Instant.now(),
                "DL-2026-001",
                "May defects",
                UUID.randomUUID(),
                null,
                null,
                UUID.randomUUID(),
                null,
                DefectListStatus.DRAFT,
                0,
                0,
                null,
                List.of()
        );
    }

    private DefectListLineDto defectListLineDto(UUID id) {
        return new DefectListLineDto(
                id,
                null,
                "Replace seal",
                null,
                null,
                null,
                1,
                0,
                0
        );
    }

    private String defectListPayload() {
        return """
                {
                  "title": "May defects",
                  "equipmentId": "%s",
                  "createdById": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private String linePayload() {
        return """
                {
                  "description": "Replace seal",
                  "requiredQuantity": 1,
                  "estimatedLaborHours": 0,
                  "estimatedCost": 0
                }
                """;
    }
}

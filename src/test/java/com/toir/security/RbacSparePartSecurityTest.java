package com.toir.security;

import com.toir.controller.SparePartController;
import com.toir.dto.sparepart.SparePartDto;
import com.toir.dto.sparepart.SparePartRequest;
import com.toir.service.SparePartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SparePartController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacSparePartSecurityTest.SecurityBeans.class
})
class RbacSparePartSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    SparePartService sparePartService;

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
    void unauthenticatedCannotReadSpareParts() throws Exception {
        mockMvc.perform(get("/api/v1/spare-parts?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadSpareParts() throws Exception {
        mockMvc.perform(get("/api/v1/spare-parts?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_READ)
    void sparePartReadCanReadListAndDetail() throws Exception {
        UUID sparePartId = UUID.randomUUID();
        Page<SparePartDto> page = new PageImpl<>(List.of(sparePartDto(sparePartId)));
        when(sparePartService.findAll(1, 0, null, "", null)).thenReturn(page);
        when(sparePartService.findById(sparePartId)).thenReturn(sparePartDto(sparePartId));

        mockMvc.perform(get("/api/v1/spare-parts?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/spare-parts/{id}", sparePartId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadSpareParts() throws Exception {
        when(sparePartService.findAll(1, 0, null, "", null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/spare-parts?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadSpareParts() throws Exception {
        when(sparePartService.findAll(1, 0, null, "", null)).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/spare-parts?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_CREATE)
    void sparePartCreateCanCreateSparePart() throws Exception {
        when(sparePartService.create(any(SparePartRequest.class))).thenReturn(sparePartDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/spare-parts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sparePartPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_UPDATE)
    void sparePartUpdateCanUpdateSparePart() throws Exception {
        UUID sparePartId = UUID.randomUUID();
        when(sparePartService.update(eq(sparePartId), any(SparePartRequest.class)))
                .thenReturn(sparePartDto(sparePartId));

        mockMvc.perform(put("/api/v1/spare-parts/{id}", sparePartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sparePartPayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_DELETE)
    void sparePartDeleteCanDeleteSparePart() throws Exception {
        mockMvc.perform(delete("/api/v1/spare-parts/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_READ)
    void sparePartReadCannotCreateSparePart() throws Exception {
        mockMvc.perform(post("/api/v1/spare-parts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sparePartPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_READ)
    void sparePartReadCannotUpdateSparePart() throws Exception {
        mockMvc.perform(put("/api/v1/spare-parts/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sparePartPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_READ)
    void sparePartReadCannotDeleteSparePart() throws Exception {
        mockMvc.perform(delete("/api/v1/spare-parts/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotCreateSparePart() throws Exception {
        mockMvc.perform(post("/api/v1/spare-parts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sparePartPayload()))
                .andExpect(status().isForbidden());
    }

    private SparePartDto sparePartDto(UUID id) {
        return new SparePartDto(
                id,
                "SPARE_PART",
                "SP-001",
                "Oil filter",
                "SPARE_PART",
                new SparePartDto.UnitRef("pcs", "pcs"),
                "Bosch",
                "OIL-FILTER",
                "Filter",
                1,
                0,
                0,
                0,
                0
        );
    }

    private String sparePartPayload() {
        return """
                {
                  "code": "SP-001",
                  "name": "Oil filter",
                  "kind": "SPARE_PART",
                  "unit": "pcs",
                  "minStock": 1
                }
                """;
    }
}

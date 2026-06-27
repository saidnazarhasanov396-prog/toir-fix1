package com.toir.security;

import com.toir.controller.MxikController;
import com.toir.dto.mxik.MxikDto;
import com.toir.dto.mxik.MxikNameCodeCountProjection;
import com.toir.dto.mxik.MxikNameCountProjection;
import com.toir.dto.mxik.MxikRequest;
import com.toir.service.MxikService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MxikController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacMxikSecurityTest.SecurityBeans.class
})
class RbacMxikSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    MxikService mxikService;

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
    void unauthenticatedCannotReadMxik() throws Exception {
        mockMvc.perform(get("/api/v1/mxik?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadMxik() throws Exception {
        mockMvc.perform(get("/api/v1/mxik?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_READ)
    void sparePartReadCanUseUzAssetsStyleListDetailAndCatalogEndpoints() throws Exception {
        UUID id = UUID.randomUUID();
        when(mxikService.findAll("ammonia", 0, 1)).thenReturn(new PageImpl<>(List.of(mxikDto(id))));
        when(mxikService.findById(id)).thenReturn(mxikDto(id));
        when(mxikService.listGroupCounts()).thenReturn(List.of(nameCount("Chemicals", 2)));
        when(mxikService.listClassCounts("Chemicals")).thenReturn(List.of(nameCount("Nitrogen compounds", 1)));
        when(mxikService.listPositionCounts("Nitrogen compounds")).thenReturn(List.of(nameCount("Fertilizer inputs", 1)));
        when(mxikService.listSubPositionCounts("Fertilizer inputs")).thenReturn(List.of(nameCount("Ammonia products", 1)));
        when(mxikService.listMxikCounts("Ammonia products")).thenReturn(List.of(nameCodeCount("Ammonia", "40112001001000000", 1)));

        mockMvc.perform(get("/api/v1/mxik?searchParam=ammonia&page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].kod").value("40112001001000000"));
        mockMvc.perform(get("/api/v1/mxik/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameUzLatn").value("Ammiak"));
        mockMvc.perform(get("/api/v1/mxik/catalog/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Chemicals"));
        mockMvc.perform(get("/api/v1/mxik/catalog/classes").param("groupName", "Chemicals"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/mxik/catalog/positions").param("className", "Nitrogen compounds"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/mxik/catalog/sub-positions").param("positionName", "Fertilizer inputs"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/mxik/catalog/mxiks").param("subPositionName", "Ammonia products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("40112001001000000"));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_CREATE)
    void sparePartCreateCanCreateMxikThroughUzAssetsStyleRoute() throws Exception {
        UUID id = UUID.randomUUID();
        when(mxikService.create(any(MxikRequest.class))).thenReturn(mxikDto(id));

        mockMvc.perform(post("/api/v1/mxik/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mxikPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_UPDATE)
    void sparePartUpdateCanUpdateMxikThroughUzAssetsStyleRoute() throws Exception {
        UUID id = UUID.randomUUID();
        when(mxikService.update(eq(id), any(MxikRequest.class))).thenReturn(mxikDto(id));

        mockMvc.perform(put("/api/v1/mxik/update/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mxikPayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_DELETE)
    void sparePartDeleteCanDeleteMxikThroughUzAssetsStyleRoute() throws Exception {
        mockMvc.perform(delete("/api/v1/mxik/delete/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.SPARE_PART_READ)
    void readPermissionCannotMutateMxik() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/mxik/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mxikPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/mxik/update/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mxikPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/mxik/delete/{id}", id))
                .andExpect(status().isForbidden());
    }

    private MxikDto mxikDto(UUID id) {
        return new MxikDto(
                id,
                "Ammonia",
                "Ammiak",
                "Аммиак",
                "40112001001000000",
                "PRODUCT",
                "Chemicals",
                "Химикаты",
                "Кимёвий моддалар",
                "Nitrogen compounds",
                "Соединения азота",
                "Азот бирикмалари",
                "Fertilizer inputs",
                "Сырье для удобрений",
                "Уғит хомашёси",
                "Ammonia products",
                "Аммиачная продукция",
                "Аммиак маҳсулотлари",
                "NAVOIYAZOT",
                "Навоиазот",
                "Навоиазот",
                "Liquid",
                "Жидкость",
                "Суюқлик",
                "1234567890123"
        );
    }

    private MxikNameCountProjection nameCount(String name, long count) {
        return new MxikNameCountProjection() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private MxikNameCodeCountProjection nameCodeCount(String name, String code, long count) {
        return new MxikNameCodeCountProjection() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getCode() {
                return code;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private String mxikPayload() {
        return """
                {
                  "name": "Ammonia",
                  "nameUzLatn": "Ammiak",
                  "nameRu": "Аммиак",
                  "kod": "40112001001000000",
                  "type": "PRODUCT",
                  "groupName": "Chemicals",
                  "className": "Nitrogen compounds",
                  "positionName": "Fertilizer inputs",
                  "subPositionName": "Ammonia products",
                  "brandName": "NAVOIYAZOT",
                  "attributeName": "Liquid",
                  "barcode": "1234567890123"
                }
                """;
    }
}

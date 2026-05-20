package com.toir.security;

import com.toir.controller.WarehouseReorderController;
import com.toir.dto.warehouse.ReorderStatsDto;
import com.toir.service.WarehouseReorderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WarehouseReorderController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWarehouseReorderStatsSecurityTest.SecurityBeans.class
})
class RbacWarehouseReorderStatsSecurityTest {

    private static final String STATS_URL = "/api/v1/warehouses/reorder/stats";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WarehouseReorderService reorderService;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties props = new CorsProperties();
            props.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return props;
        }
    }

    // ------------------------------------------------------------------ //
    //  Unauthenticated                                                    //
    // ------------------------------------------------------------------ //

    @Test
    void unauthenticatedCannotReadReorderStats() throws Exception {
        mockMvc.perform(get(STATS_URL))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ //
    //  Wrong permission                                                   //
    // ------------------------------------------------------------------ //

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadReorderStats() throws Exception {
        mockMvc.perform(get(STATS_URL))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ //
    //  Authorised roles                                                   //
    // ------------------------------------------------------------------ //

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCanReadReorderStats() throws Exception {
        when(reorderService.getStats(isNull()))
                .thenReturn(new ReorderStatsDto(2, 3, 5, 2));

        mockMvc.perform(get(STATS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.critical").value(2))
                .andExpect(jsonPath("$.warning").value(3))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.affectedWarehouses").value(2));
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadReorderStats() throws Exception {
        when(reorderService.getStats(isNull()))
                .thenReturn(new ReorderStatsDto(0, 1, 1, 1));

        mockMvc.perform(get(STATS_URL))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadReorderStats() throws Exception {
        when(reorderService.getStats(isNull()))
                .thenReturn(new ReorderStatsDto(0, 0, 0, 0));

        mockMvc.perform(get(STATS_URL))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ //
    //  Suggestions endpoint should also still be guarded the same way    //
    // ------------------------------------------------------------------ //

    @Test
    void unauthenticatedCannotReadReorderSuggestions() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses/reorder/suggestions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadReorderSuggestions() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses/reorder/suggestions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCanReadReorderSuggestions() throws Exception {
        when(reorderService.suggestions(isNull(), org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/warehouses/reorder/suggestions"))
                .andExpect(status().isOk());
    }
}

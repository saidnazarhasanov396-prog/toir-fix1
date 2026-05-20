package com.toir.security;

import com.toir.controller.WarehouseSparePartsStatsController;
import com.toir.dto.warehouse.SparePartsWarehouseStatsResponse;
import com.toir.service.WarehouseSparePartsStatsService;
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

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WarehouseSparePartsStatsController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWarehouseSparePartsStatsSecurityTest.SecurityBeans.class
})
class RbacWarehouseSparePartsStatsSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WarehouseSparePartsStatsService statsService;

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
    void unauthenticatedCannotReadStats() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses/spare-parts/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadStats() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses/spare-parts/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCanReadStats() throws Exception {
        when(statsService.getStats(isNull())).thenReturn(new SparePartsWarehouseStatsResponse(1, 1, 1, 1));

        mockMvc.perform(get("/api/v1/warehouses/spare-parts/stats"))
                .andExpect(status().isOk());
    }
}

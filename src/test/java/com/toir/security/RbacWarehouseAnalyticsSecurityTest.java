package com.toir.security;

import com.toir.controller.WarehouseAnalyticsController;
import com.toir.dto.warehouseanalytics.WarehouseAnalyticsOverviewDto;
import com.toir.service.WarehouseAnalyticsService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WarehouseAnalyticsController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWarehouseAnalyticsSecurityTest.SecurityBeans.class
})
class RbacWarehouseAnalyticsSecurityTest {

    private static final String OVERVIEW_URL = "/api/v1/warehouse/analytics/overview";

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WarehouseAnalyticsService service;

    @TestConfiguration
    static class SecurityBeans {
        @Bean
        CorsProperties corsProperties() {
            CorsProperties props = new CorsProperties();
            props.setAllowedOriginPatterns(List.of("http://localhost:3000"));
            return props;
        }
    }

    @Test
    void unauthenticatedCannotReadWarehouseAnalytics() throws Exception {
        mockMvc.perform(get(OVERVIEW_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadWarehouseAnalytics() throws Exception {
        mockMvc.perform(get(OVERVIEW_URL))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCanReadWarehouseAnalytics() throws Exception {
        when(service.overview(any()))
                .thenReturn(new WarehouseAnalyticsOverviewDto(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get(OVERVIEW_URL))
                .andExpect(status().isOk());
    }
}

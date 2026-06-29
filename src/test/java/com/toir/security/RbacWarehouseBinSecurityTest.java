package com.toir.security;

import com.toir.controller.WarehouseBinController;
import com.toir.dto.warehouse.WarehouseBinDto;
import com.toir.enums.WarehouseQualityZoneType;
import com.toir.service.warehouse.WarehouseBinService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WarehouseBinController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWarehouseBinSecurityTest.SecurityBeans.class
})
class RbacWarehouseBinSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WarehouseBinService service;

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
    void unauthenticatedCannotReadBins() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/bins", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadBins() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/bins", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_BIN_READ)
    void warehouseBinReadCanReadBins() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(service.list(
                eq(warehouseId),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                eq(20)
        )).thenReturn(new PageImpl<>(List.of(binDto(warehouseId)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/bins", warehouseId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_BIN_MANAGE)
    void warehouseBinManageCanCreateAndBlockBins() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        when(service.create(eq(warehouseId), any())).thenReturn(binDto(warehouseId));
        when(service.block(eq(warehouseId), eq(binId), any())).thenReturn(binDto(warehouseId));

        mockMvc.perform(post("/api/v1/warehouses/{warehouseId}/bins", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"A-01-02-03\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/warehouses/{warehouseId}/bins/{binId}/block", warehouseId, binId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"audit\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_BIN_READ)
    void warehouseBinReadCannotCreateBin() throws Exception {
        mockMvc.perform(post("/api/v1/warehouses/{warehouseId}/bins", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"A-01-02-03\"}"))
                .andExpect(status().isForbidden());
    }

    private WarehouseBinDto binDto(UUID warehouseId) {
        return new WarehouseBinDto(
                UUID.randomUUID(),
                warehouseId,
                "A-01-02-03",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                WarehouseQualityZoneType.STORAGE,
                null,
                null,
                true,
                true,
                false,
                null,
                null,
                false,
                true,
                null,
                null,
                null,
                null,
                Instant.now()
        );
    }
}

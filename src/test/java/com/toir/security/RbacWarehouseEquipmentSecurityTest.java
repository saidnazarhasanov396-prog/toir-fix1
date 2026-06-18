package com.toir.security;

import com.toir.controller.WarehouseController;
import com.toir.dto.warehouse.WarehouseEquipmentItemDto;
import com.toir.enums.WarehouseEquipmentStatus;
import com.toir.service.WarehouseEquipmentItemService;
import com.toir.service.WarehouseService;
import com.toir.service.warehouse.ToirWarehouseQueryService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WarehouseController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWarehouseEquipmentSecurityTest.SecurityBeans.class
})
class RbacWarehouseEquipmentSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WarehouseService warehouseService;

    @MockBean
    WarehouseEquipmentItemService warehouseEquipmentItemService;

    @MockBean
    ToirWarehouseQueryService warehouseQueryService;

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
    void unauthenticatedCannotReadWarehouseEquipment() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/equipment", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadWarehouseEquipment() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/equipment", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_EQUIPMENT_READ)
    void warehouseEquipmentReadCanReadEquipmentAndStock() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseEquipmentItemService.list(eq(warehouseId), eq(null), eq(0), eq(1)))
                .thenReturn(new PageImpl<>(List.of(warehouseEquipmentItemDto(warehouseId)), PageRequest.of(0, 1), 1));
        when(warehouseService.findStocks(warehouseId, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/equipment?page=0&size=1", warehouseId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/stocks?page=0&size=1", warehouseId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCanReadWarehouseStocks() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseService.findStocks(warehouseId, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/stocks?page=0&size=1", warehouseId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadWarehouseEquipment() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseEquipmentItemService.list(eq(warehouseId), eq(null), eq(0), eq(1)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/equipment?page=0&size=1", warehouseId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadWarehouseEquipment() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseEquipmentItemService.list(eq(warehouseId), eq(null), eq(0), eq(1)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/warehouses/{warehouseId}/equipment?page=0&size=1", warehouseId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_EQUIPMENT_STATUS_UPDATE)
    void warehouseEquipmentStatusUpdateCanUnassignEquipment() throws Exception {
        mockMvc.perform(delete("/api/v1/warehouses/{warehouseId}/equipment/{equipmentId}", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_EQUIPMENT_READ)
    void warehouseEquipmentReadCannotUnassignEquipment() throws Exception {
        mockMvc.perform(delete("/api/v1/warehouses/{warehouseId}/equipment/{equipmentId}", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    private WarehouseEquipmentItemDto warehouseEquipmentItemDto(UUID warehouseId) {
        return new WarehouseEquipmentItemDto(
                UUID.randomUUID(),
                warehouseId,
                UUID.randomUUID(),
                WarehouseEquipmentStatus.AVAILABLE,
                true,
                Instant.now()
        );
    }
}

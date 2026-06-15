package com.toir.security;

import com.toir.controller.WarehouseController;
import com.toir.dto.warehouse.WarehouseDto;
import com.toir.dto.warehouse.WarehouseRequest;
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

@WebMvcTest(controllers = WarehouseController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWarehouseSecurityTest.SecurityBeans.class
})
class RbacWarehouseSecurityTest {

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
    void unauthenticatedCannotReadWarehouses() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadWarehouses() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_READ)
    void warehouseReadCanReadListAndDetail() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseService.findAll(null, null, null, null, null))
                .thenReturn(List.of(warehouseDto(warehouseId)));
        when(warehouseService.findById(warehouseId)).thenReturn(warehouseDto(warehouseId));

        mockMvc.perform(get("/api/v1/warehouses?page=0&size=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/warehouses/{id}", warehouseId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadWarehouses() throws Exception {
        when(warehouseService.findAll(null, null, null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/warehouses?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadWarehouses() throws Exception {
        when(warehouseService.findAll(null, null, null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/warehouses?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_CREATE)
    void warehouseCreateCanCreateWarehouse() throws Exception {
        when(warehouseService.create(any(WarehouseRequest.class))).thenReturn(warehouseDto(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehousePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_READ)
    void warehouseReadCannotCreateWarehouse() throws Exception {
        mockMvc.perform(post("/api/v1/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehousePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_UPDATE)
    void warehouseUpdateCanUpdateWarehouse() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseService.update(eq(warehouseId), any(WarehouseRequest.class))).thenReturn(warehouseDto(warehouseId));

        mockMvc.perform(put("/api/v1/warehouses/{id}", warehouseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehousePayload()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_READ)
    void warehouseReadCannotUpdateWarehouse() throws Exception {
        mockMvc.perform(put("/api/v1/warehouses/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(warehousePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_DELETE)
    void warehouseDeleteCanDeleteWarehouse() throws Exception {
        mockMvc.perform(delete("/api/v1/warehouses/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_READ)
    void warehouseReadCannotDeleteWarehouse() throws Exception {
        mockMvc.perform(delete("/api/v1/warehouses/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    private WarehouseDto warehouseDto(UUID id) {
        return new WarehouseDto(
                id,
                "WH-001",
                "Main warehouse",
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                new WarehouseDto.Summary(0, 0, 0, 0),
                List.of()
        );
    }

    private String warehousePayload() {
        return """
                {
                  "name": "Main warehouse",
                  "active": true
                }
                """;
    }
}

package com.toir.security;

import com.toir.controller.InventoryCountSessionController;
import com.toir.dto.inventorycount.InventoryCountLineDto;
import com.toir.dto.inventorycount.InventoryCountSessionDto;
import com.toir.enums.InventoryCountLineStatus;
import com.toir.enums.InventoryCountScopeType;
import com.toir.enums.InventoryCountSessionStatus;
import com.toir.enums.WarehouseStockStatus;
import com.toir.service.warehouse.InventoryCountSessionService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InventoryCountSessionController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacInventoryCountSecurityTest.SecurityBeans.class
})
class RbacInventoryCountSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    InventoryCountSessionService service;

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
    void unauthenticatedCannotListCountSessions() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/count-sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INVENTORY_READ)
    void inventoryReadCanListAndGet() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(service.findAll(null, null, 0, 20)).thenReturn(new PageImpl<>(List.of(dto(sessionId)), PageRequest.of(0, 20), 1));
        when(service.findById(sessionId)).thenReturn(dto(sessionId));

        mockMvc.perform(get("/api/v1/inventory/count-sessions"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inventory/count-sessions/{id}", sessionId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCreateExecuteOrApprove() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/inventory/count-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/lines/{lineId}/count", sessionId, lineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedQty\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/approve", sessionId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_COUNT_CREATE)
    void countCreateCanCreateOpenAndCancel() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(service.create(any())).thenReturn(dto(sessionId));
        when(service.open(sessionId)).thenReturn(dto(sessionId));
        when(service.cancel(eq(sessionId), eq("cancel"))).thenReturn(dto(sessionId));

        mockMvc.perform(post("/api/v1/inventory/count-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/open", sessionId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/cancel", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cancel\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_COUNT_EXECUTE)
    void countExecuteCanCountLine() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        when(service.countLine(eq(sessionId), eq(lineId), any())).thenReturn(dto(sessionId));

        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/lines/{lineId}/count", sessionId, lineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedQty\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_COUNT_APPROVE)
    void countApproveCanReviewApproveAndPost() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(service.review(eq(sessionId), any())).thenReturn(dto(sessionId));
        when(service.approve(sessionId)).thenReturn(dto(sessionId));
        when(service.postAdjustments(sessionId)).thenReturn(dto(sessionId));

        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/review", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/approve", sessionId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inventory/count-sessions/{id}/post-adjustments", sessionId))
                .andExpect(status().isOk());
    }

    private String validCreateBody() {
        return """
                {
                  "warehouseId": "%s",
                  "scopeType": "WAREHOUSE"
                }
                """.formatted(UUID.randomUUID());
    }

    private InventoryCountSessionDto dto(UUID sessionId) {
        UUID warehouseId = UUID.randomUUID();
        return new InventoryCountSessionDto(
                sessionId,
                "IC-2026-00001",
                warehouseId,
                InventoryCountSessionStatus.DRAFT,
                InventoryCountScopeType.WAREHOUSE,
                null,
                null,
                null,
                null,
                null,
                false,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                "CNT-1",
                null,
                List.of(new InventoryCountLineDto(
                        UUID.randomUUID(),
                        warehouseId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        LocalDate.of(2028, 1, 31),
                        WarehouseStockStatus.AVAILABLE,
                        BigDecimal.ONE,
                        null,
                        null,
                        null,
                        InventoryCountLineStatus.OPEN,
                        null,
                        null,
                        null
                )),
                Instant.now(),
                Instant.now()
        );
    }
}

package com.toir.security;

import com.toir.controller.StockMovementController;
import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.enums.StockMovementType;
import com.toir.service.StockMovementService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StockMovementController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacStockMovementSecurityTest.SecurityBeans.class
})
class RbacStockMovementSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    StockMovementService stockMovementService;

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
    void unauthenticatedCannotReadStockMovements() throws Exception {
        mockMvc.perform(get("/api/v1/stock-movements?page=0&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadStockMovements() throws Exception {
        mockMvc.perform(get("/api/v1/stock-movements?page=0&size=1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCanReadStockMovements() throws Exception {
        when(stockMovementService.findAll()).thenReturn(List.of(stockMovementDto(StockMovementType.RECEIPT)));

        mockMvc.perform(get("/api/v1/stock-movements?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadStockMovements() throws Exception {
        when(stockMovementService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stock-movements?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadStockMovements() throws Exception {
        when(stockMovementService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stock-movements?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_RECEIVE)
    void stockReceiveCanCreateReceiptMovement() throws Exception {
        when(stockMovementService.create(any())).thenReturn(stockMovementDto(StockMovementType.RECEIPT));

        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockMovementPayload(StockMovementType.RECEIPT)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_ISSUE)
    void stockIssueCanCreateIssueMovement() throws Exception {
        when(stockMovementService.create(any())).thenReturn(stockMovementDto(StockMovementType.ISSUE));

        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockMovementPayload(StockMovementType.ISSUE)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_MOVE)
    void stockMoveCanCreateTransferMovement() throws Exception {
        when(stockMovementService.create(any())).thenReturn(stockMovementDto(StockMovementType.TRANSFER));

        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockMovementPayload(StockMovementType.TRANSFER)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_ADJUST)
    void stockAdjustCanCreateAdjustmentMovement() throws Exception {
        when(stockMovementService.create(any())).thenReturn(stockMovementDto(StockMovementType.ADJUSTMENT));

        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockMovementPayload(StockMovementType.ADJUSTMENT)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCannotCreateStockMovement() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockMovementPayload(StockMovementType.ISSUE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCreateStockMovement() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockMovementPayload(StockMovementType.ISSUE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.READ_LEGACY)
    void legacyReadCannotCreateStockMovement() throws Exception {
        mockMvc.perform(post("/api/v1/stock-movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockMovementPayload(StockMovementType.ISSUE)))
                .andExpect(status().isForbidden());
    }

    private StockMovementDto stockMovementDto(StockMovementType type) {
        return new StockMovementDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                type,
                1,
                10.0,
                "DOC-1",
                UUID.randomUUID(),
                Instant.now(),
                null
        );
    }

    private String stockMovementPayload(StockMovementType type) {
        return """
                {
                  "warehouseId": "%s",
                  "sparePartId": "%s",
                  "type": "%s",
                  "quantity": 1
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), type.name());
    }
}

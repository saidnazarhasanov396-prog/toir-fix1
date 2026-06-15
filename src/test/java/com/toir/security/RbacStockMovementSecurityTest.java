package com.toir.security;

import com.toir.controller.StockMovementController;
import com.toir.dto.stockmovement.StockMovementFileDto;
import com.toir.dto.stockmovement.StockMovementDto;
import com.toir.enums.StockMovementType;
import com.toir.service.StockMovementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        when(stockMovementService.findAll(0, 1, null, null, null, null, null, null, null)).thenReturn(new PageImpl<>(
                List.of(stockMovementDto(StockMovementType.RECEIPT)),
                PageRequest.of(0, 1),
                1));

        mockMvc.perform(get("/api/v1/stock-movements?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].warehouseName").value("Main Warehouse"))
                .andExpect(jsonPath("$.content[0].sparePartName").value("Bearing 6205"))
                .andExpect(jsonPath("$.content[0].workOrderName").value("Pump repair"))
                .andExpect(jsonPath("$.content[0].workOrderNumber").value("WO-42"))
                .andExpect(jsonPath("$.content[0].createdByFullName").value("Jane Smith"));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCanListMovementFiles() throws Exception {
        UUID movementId = UUID.randomUUID();
        when(stockMovementService.listFiles(eq(movementId), any()))
                .thenReturn(List.of(stockMovementFileDto()));

        mockMvc.perform(get("/api/v1/stock-movements/{movementId}/files", movementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalName").value("invoice.pdf"));
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCannotUploadMovementFiles() throws Exception {
        mockMvc.perform(multipart("/api/v1/stock-movements/{movementId}/files", UUID.randomUUID())
                        .file("files", "pdf".getBytes()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_RECEIVE)
    void stockReceiveCanUploadReceiptMovementFiles() throws Exception {
        UUID movementId = UUID.randomUUID();
        when(stockMovementService.movementType(movementId)).thenReturn(StockMovementType.RECEIPT);
        when(stockMovementService.attachFiles(eq(movementId), any(), any()))
                .thenReturn(List.of(stockMovementFileDto()));

        mockMvc.perform(multipart("/api/v1/stock-movements/{movementId}/files", movementId)
                        .file("files", "pdf".getBytes()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_RECEIVE)
    void stockReceiveCannotUploadIssueMovementFiles() throws Exception {
        UUID movementId = UUID.randomUUID();
        when(stockMovementService.movementType(movementId)).thenReturn(StockMovementType.ISSUE);

        mockMvc.perform(multipart("/api/v1/stock-movements/{movementId}/files", movementId)
                        .file("files", "pdf".getBytes()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_ISSUE)
    void stockIssueCanDeleteIssueMovementFiles() throws Exception {
        UUID movementId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(stockMovementService.movementType(movementId)).thenReturn(StockMovementType.ISSUE);

        mockMvc.perform(delete("/api/v1/stock-movements/{movementId}/files/{fileId}", movementId, fileId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "SYSTEM_ADMIN")
    void systemAdminCanReadStockMovements() throws Exception {
        when(stockMovementService.findAll(0, 1, null, null, null, null, null, null, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        mockMvc.perform(get("/api/v1/stock-movements?page=0&size=1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WILDCARD)
    void wildcardCanReadStockMovements() throws Exception {
        when(stockMovementService.findAll(0, 1, null, null, null, null, null, null, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

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
                "Main Warehouse",
                UUID.randomUUID(),
                "Bearing 6205",
                null,
                "WO-42",
                "Pump repair",
                type,
                1,
                10.0,
                "DOC-1",
                UUID.randomUUID(),
                "Jane Smith",
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

    private StockMovementFileDto stockMovementFileDto() {
        return new StockMovementFileDto(
                UUID.randomUUID(),
                "invoice.pdf",
                "application/pdf",
                100L,
                "/api/v1/stock-movements/movement-1/files/file-1/download"
        );
    }
}

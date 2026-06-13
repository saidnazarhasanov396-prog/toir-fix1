package com.toir.security;

import com.toir.controller.InventoryController;
import com.toir.dto.inventory.InventoryIssueDto;
import com.toir.dto.inventory.InventoryReceiptDto;
import com.toir.dto.inventory.InventoryStatisticsDto;
import com.toir.dto.inventory.InventoryTransactionDto;
import com.toir.enums.InventoryTransactionType;
import com.toir.service.InventoryTransactionService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InventoryController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacInventoryTransactionSecurityTest.SecurityBeans.class
})
class RbacInventoryTransactionSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    InventoryTransactionService service;

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
    void unauthenticatedCannotReadInventoryTransactions() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INVENTORY_READ)
    void inventoryReadCanReadHistoryAndStatistics() throws Exception {
        when(service.findAll(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(transactionDto()), PageRequest.of(0, 20), 1));
        when(service.statistics(any(), any(), any())).thenReturn(new InventoryStatisticsDto(
                1,
                1,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE
        ));

        mockMvc.perform(get("/api/v1/inventory/transactions"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inventory/statistics"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INVENTORY_RECEIPT)
    void inventoryReceiptCanCreateReceipt() throws Exception {
        when(service.createReceipt(any())).thenReturn(receiptDto());

        mockMvc.perform(post("/api/v1/inventory/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptPayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INVENTORY_ISSUE)
    void inventoryIssueCanCreateIssue() throws Exception {
        when(service.createIssue(any())).thenReturn(issueDto());

        mockMvc.perform(post("/api/v1/inventory/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INVENTORY_READ)
    void inventoryReadCannotCreateReceiptOrIssue() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptPayload()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/inventory/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(issuePayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReadInventory() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/transactions"))
                .andExpect(status().isForbidden());
    }

    private InventoryTransactionDto transactionDto() {
        return new InventoryTransactionDto(
                UUID.randomUUID(),
                InventoryTransactionType.RECEIPT,
                UUID.randomUUID(),
                "Main Warehouse",
                UUID.randomUUID(),
                "Engine Oil",
                BigDecimal.ONE,
                "PCS",
                BigDecimal.TEN,
                BigDecimal.TEN,
                "Supplier",
                null,
                null,
                UUID.randomUUID(),
                "Jane Smith",
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 6, 13),
                "DOC-1",
                "Comment",
                null,
                null
        );
    }

    private InventoryReceiptDto receiptDto() {
        return new InventoryReceiptDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Main Warehouse",
                UUID.randomUUID(),
                "Engine Oil",
                BigDecimal.ONE,
                "PCS",
                BigDecimal.TEN,
                BigDecimal.TEN,
                "Supplier",
                UUID.randomUUID(),
                "Jane Smith",
                LocalDate.of(2026, 6, 13),
                "DOC-1",
                "Comment"
        );
    }

    private InventoryIssueDto issueDto() {
        return new InventoryIssueDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Main Warehouse",
                UUID.randomUUID(),
                "Engine Oil",
                BigDecimal.ONE,
                "PCS",
                UUID.randomUUID(),
                "Taken By",
                UUID.randomUUID(),
                "Jane Smith",
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 6, 13),
                "DOC-1",
                "Comment"
        );
    }

    private String receiptPayload() {
        return """
                {
                  "warehouseId": "%s",
                  "sparePartId": "%s",
                  "quantity": 1,
                  "unit": "PCS",
                  "unitPrice": 10,
                  "receiptDate": "2026-06-13",
                  "responsiblePersonId": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private String issuePayload() {
        return """
                {
                  "warehouseId": "%s",
                  "sparePartId": "%s",
                  "quantity": 1,
                  "unit": "PCS",
                  "issueDate": "2026-06-13",
                  "takenById": "%s",
                  "responsiblePersonId": "%s"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}

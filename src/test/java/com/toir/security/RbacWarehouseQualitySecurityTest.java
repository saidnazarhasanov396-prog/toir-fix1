package com.toir.security;

import com.toir.controller.WarehouseQualityController;
import com.toir.dto.warehouse.WarehouseQualityTransferDto;
import com.toir.dto.warehouse.WarehouseWriteoffRequestDto;
import com.toir.enums.WarehouseStockStatus;
import com.toir.enums.WarehouseWriteoffStatus;
import com.toir.service.warehouse.WarehouseQualityService;
import com.toir.service.warehouse.WmsOperationsQueryService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WarehouseQualityController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWarehouseQualitySecurityTest.SecurityBeans.class
})
class RbacWarehouseQualitySecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WarehouseQualityService service;

    @MockBean
    WmsOperationsQueryService wmsOperationsQueryService;

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
    void unauthenticatedCannotTransferStatus() throws Exception {
        mockMvc.perform(post("/api/v1/warehouse/quality/status-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotCreateWriteoff() throws Exception {
        mockMvc.perform(post("/api/v1/warehouse/writeoffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_TASK_EXECUTE)
    void warehouseTaskExecuteCanTransferStatus() throws Exception {
        when(service.transferStatus(any())).thenReturn(new WarehouseQualityTransferDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                WarehouseStockStatus.AVAILABLE,
                WarehouseStockStatus.QUARANTINE,
                BigDecimal.ONE,
                "POSTED"
        ));

        mockMvc.perform(post("/api/v1/warehouse/quality/status-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_WRITEOFF_REQUEST)
    void writeoffRequestCanCreateSubmitAndTransferStatus() throws Exception {
        UUID writeoffId = UUID.randomUUID();
        when(service.transferStatus(any())).thenReturn(new WarehouseQualityTransferDto(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                WarehouseStockStatus.AVAILABLE, WarehouseStockStatus.QUARANTINE, BigDecimal.ONE, "POSTED"));
        when(service.createWriteoffRequest(any())).thenReturn(dto(writeoffId, WarehouseWriteoffStatus.DRAFT));
        when(service.submitForApproval(writeoffId)).thenReturn(dto(writeoffId, WarehouseWriteoffStatus.PENDING_APPROVAL));

        mockMvc.perform(post("/api/v1/warehouse/quality/status-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/warehouse/writeoffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/warehouse/writeoffs/{id}/submit", writeoffId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_WRITEOFF_APPROVE)
    void writeoffApproveCanApprovePostAndReject() throws Exception {
        UUID writeoffId = UUID.randomUUID();
        when(service.approve(eq(writeoffId), any())).thenReturn(dto(writeoffId, WarehouseWriteoffStatus.APPROVED));
        when(service.post(writeoffId)).thenReturn(dto(writeoffId, WarehouseWriteoffStatus.POSTED));
        when(service.reject(eq(writeoffId), any())).thenReturn(dto(writeoffId, WarehouseWriteoffStatus.REJECTED));

        mockMvc.perform(post("/api/v1/warehouse/writeoffs/{id}/approve", writeoffId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/warehouse/writeoffs/{id}/post", writeoffId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/warehouse/writeoffs/{id}/reject", writeoffId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private WarehouseWriteoffRequestDto dto(UUID id, WarehouseWriteoffStatus status) {
        return new WarehouseWriteoffRequestDto(
                id,
                "WOFF-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                null,
                LocalDate.of(2028, 1, 31),
                WarehouseStockStatus.WRITEOFF_PENDING,
                BigDecimal.ONE,
                "obsolete",
                status,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                "WOFF-ACT-1",
                null
        );
    }
}

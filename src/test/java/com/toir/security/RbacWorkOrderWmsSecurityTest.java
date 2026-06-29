package com.toir.security;

import com.toir.controller.WorkOrderWmsController;
import com.toir.dto.materialusage.RepairMaterialUsageDto;
import com.toir.dto.reservation.ReservationDto;
import com.toir.dto.warehouse.WarehouseTaskDto;
import com.toir.dto.workorder.WorkOrderMaterialReturnDto;
import com.toir.enums.ReservationStatus;
import com.toir.enums.WarehouseTaskPriority;
import com.toir.enums.WarehouseTaskSourceType;
import com.toir.enums.WarehouseTaskStatus;
import com.toir.enums.WarehouseTaskType;
import com.toir.service.warehouse.WorkOrderWmsService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WorkOrderWmsController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacWorkOrderWmsSecurityTest.SecurityBeans.class
})
class RbacWorkOrderWmsSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    WorkOrderWmsService service;

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
    void unauthenticatedCannotReserve() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/wms-reservations", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReservationBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.USER_READ)
    void unrelatedPermissionCannotReserve() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/wms-reservations", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReservationBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.WAREHOUSE_PICK)
    void warehousePickCanReserveCreatePickListConfirmAndReturn() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        UUID pickListId = UUID.randomUUID();
        when(service.reserve(eq(workOrderId), any())).thenReturn(List.of(new ReservationDto(
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                workOrderId,
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                1,
                ReservationStatus.ACTIVE
        )));
        when(service.createPickList(eq(workOrderId), any())).thenReturn(taskDto(workOrderId));
        when(service.confirmPick(eq(workOrderId), eq(pickListId), any())).thenReturn(List.of(new RepairMaterialUsageDto(
                UUID.randomUUID(),
                workOrderId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                null
        )));

        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/wms-reservations", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReservationBody()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/pick-list", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/pick-list/{pickListId}/confirm", workOrderId, pickListId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validConfirmBody()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/material-returns", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCannotCreatePickList() throws Exception {
        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/pick-list", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.INVENTORY_RETURN)
    void inventoryReturnCanReturnMaterial() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(service.returnMaterial(eq(workOrderId), any())).thenReturn(new WorkOrderMaterialReturnDto(
                UUID.randomUUID(),
                workOrderId,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.ONE,
                "POSTED"
        ));

        mockMvc.perform(post("/api/v1/work-orders/{workOrderId}/material-returns", workOrderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private String validReservationBody() {
        return """
                {
                  "lines": [{
                    "requirementId": "%s",
                    "warehouseId": "%s",
                    "sparePartId": "%s",
                    "quantity": 1
                  }]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private String validConfirmBody() {
        return """
                {
                  "lines": [{
                    "requirementId": "%s",
                    "reservationId": "%s",
                    "warehouseId": "%s",
                    "sparePartId": "%s",
                    "quantity": 1
                  }]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private WarehouseTaskDto taskDto(UUID workOrderId) {
        return new WarehouseTaskDto(
                UUID.randomUUID(),
                "WT-2026-00008",
                WarehouseTaskType.PICK,
                WarehouseTaskStatus.OPEN,
                WarehouseTaskPriority.NORMAL,
                UUID.randomUUID(),
                WarehouseTaskSourceType.WORK_ORDER,
                workOrderId,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Instant.now(),
                Instant.now()
        );
    }
}

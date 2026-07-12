package com.toir.security;

import com.toir.controller.ReservationController;
import com.toir.dto.reservation.ReservationDto;
import com.toir.enums.ReservationStatus;
import com.toir.service.ReservationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReservationController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityAccessService.class,
        RbacReservationSecurityTest.SecurityBeans.class
})
class RbacReservationSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtService jwtService;

    @MockBean
    ReservationService reservationService;

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
    void unauthenticatedCannotReadReservations() throws Exception {
        mockMvc.perform(get("/api/v1/reservations").param("workOrderId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_READ)
    void stockReadCanReadReservationsButCannotMutate() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        when(reservationService.findByWorkOrder(workOrderId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/reservations").param("workOrderId", workOrderId.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_MOVE)
    void stockMoveCanReserveAndCancel() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(reservationService.reserve(any())).thenReturn(reservationDto(ReservationStatus.ACTIVE));
        when(reservationService.cancel(eq(reservationId))).thenReturn(reservationDto(ReservationStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationPayload()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/reservations/{id}/cancel", reservationId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = PermissionConstants.STOCK_ISSUE)
    void stockIssueCanFulfillReservation() throws Exception {
        UUID reservationId = UUID.randomUUID();
        when(reservationService.fulfill(eq(reservationId))).thenReturn(reservationDto(ReservationStatus.FULFILLED));

        mockMvc.perform(post("/api/v1/reservations/{id}/fulfill", reservationId))
                .andExpect(status().isOk());
    }

    private String reservationPayload() {
        return """
                {
                  "warehouseStockId": "%s",
                  "workOrderId": "%s",
                  "requirementId": "%s",
                  "reservedById": "%s",
                  "quantity": "1.0000"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private ReservationDto reservationDto(ReservationStatus status) {
        return new ReservationDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                java.math.BigDecimal.ONE,
                status
        );
    }
}
